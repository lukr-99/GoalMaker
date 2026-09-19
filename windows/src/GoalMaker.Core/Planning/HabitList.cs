using System.Globalization;
using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Planning;

/// <summary>
/// The owner's habits, their check-ins and pauses, read from the replica and changed through its outbox
/// (docs/habits.md). A day's check-in has the id <see cref="HabitRules.CheckinId"/> gives it, so both
/// devices write the same row. Every write asks for a sync.
/// </summary>
public sealed class HabitList
{
    private const string Table = "habits";
    private const string CheckinTable = "habit_checkins";
    private const string PauseTable = "habit_pauses";
    private const int MaxName = 100;
    private const int MaxEmoji = 16;
    private const int MaxUnit = 20;
    private readonly IReplica replica;
    private readonly NewRows rows;
    private readonly Action requestSync;

    public HabitList(IReplica replica, NewRows rows, Action requestSync)
    {
        this.replica = replica;
        this.rows = rows;
        this.requestSync = requestSync;
        replica.Changed += (_, table) =>
        {
            if (table is Table or CheckinTable or PauseTable)
            {
                Changed?.Invoke(this, EventArgs.Empty);
            }
        };
    }

    /// <summary>Raised after every change to the habits, their check-ins or their pauses.</summary>
    public event EventHandler? Changed;

    /// <summary>Every habit that isn't deleted, in the owner's order, archived ones last.</summary>
    public IReadOnlyList<HabitItem> All() =>
        [.. replica.All(Table).Select(ToItem).Where(habit => !habit.Deleted)
            .OrderBy(habit => habit.Archived)
            .ThenBy(habit => habit.Position)
            .ThenBy(habit => habit.Name, StringComparer.OrdinalIgnoreCase)];

    public HabitItem? Find(string id) => replica.Get(Table, id) is { } row && ToItem(row) is { Deleted: false } habit ? habit : null;

    /// <summary>Every check-in that isn't deleted.</summary>
    public IReadOnlyList<HabitCheckin> Checkins() => [.. replica.All(CheckinTable).Select(ToCheckin).Where(checkin => !checkin.Deleted)];

    /// <summary>Every pause that isn't deleted.</summary>
    public IReadOnlyList<HabitPause> Pauses() => [.. replica.All(PauseTable).Select(ToPause).Where(pause => !pause.Deleted)];

    /// <summary>Adds a habit at the end. Null when the draft isn't one the server would keep.</summary>
    public HabitItem? Add(HabitDraft draft)
    {
        if (Check(draft) is not { } clean)
        {
            return null;
        }

        var values = Values(clean);
        values["starts_on"] = clean.StartsOn.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
        values["position"] = All().Select(habit => habit.Position).DefaultIfEmpty(-1).Max() + 1;
        if (rows.Create(Table, values) is not { } row)
        {
            return null;
        }

        replica.Queue(Table, row);
        requestSync();
        return ToItem(row);
    }

    /// <summary>Changes a habit to what <paramref name="draft"/> says; its start stays.</summary>
    public bool Update(string id, HabitDraft draft) => Check(draft) is { } clean && Change(Table, id, row =>
    {
        foreach (var (column, value) in Values(clean))
        {
            row[column] = value;
        }
    });

    /// <summary>Archives a habit (off Today and the list, kept with its history) or brings it back.</summary>
    public bool SetArchived(string id, bool archived) =>
        Change(Table, id, row => row["archived_at"] = archived ? rows.Timestamp() : null);

    /// <summary>Deletes a habit softly; the server takes its check-ins and pauses with it.</summary>
    public bool Delete(string id) => Change(Table, id, row => row[SyncedTable.DeletedAt] = rows.Timestamp());

    /// <summary>
    /// Adds <paramref name="amount"/> to the day's value (a check sets it to 1) and returns the new value;
    /// a check-in that said skipped counts again. Null when the habit is gone or the amount isn't positive.
    /// </summary>
    public double? CheckIn(string habitId, DateOnly day, double amount = 1)
    {
        if (Find(habitId) is not { } habit || amount <= 0 || !double.IsFinite(amount))
        {
            return null;
        }

        var before = CheckinOn(habitId, day) is { Skipped: false } checkin ? checkin.Value : 0;
        var value = habit.Measure == HabitRules.Check ? 1 : before + amount;
        Write(habitId, day, value, skipped: false);
        return value;
    }

    /// <summary>
    /// One tap on a habit's ring for <paramref name="day"/>: a check toggles, a count adds one. False for
    /// an amount, which asks for its value, and when the habit is gone.
    /// </summary>
    public bool Tap(string habitId, DateOnly day)
    {
        if (Find(habitId) is not { } habit)
        {
            return false;
        }

        switch (habit.Measure)
        {
            case HabitRules.Check:
                var checked_ = CheckinOn(habitId, day) is { Skipped: false, Value: >= 1 };
                Write(habitId, day, checked_ ? 0 : 1, skipped: false);
                return true;
            case HabitRules.Count:
                return CheckIn(habitId, day) is not null;
            default:
                return false;
        }
    }

    /// <summary>Sets the day's value exactly: 0 takes a check back, and an undo puts the old value back.</summary>
    public bool SetValue(string habitId, DateOnly day, double value)
    {
        if (Find(habitId) is null || value < 0 || !double.IsFinite(value))
        {
            return false;
        }

        Write(habitId, day, value, skipped: false);
        return true;
    }

    /// <summary>Marks the period holding <paramref name="day"/> skipped (sick, travelling), or takes the skip back.</summary>
    public bool Skip(string habitId, DateOnly day, bool skipped = true)
    {
        if (Find(habitId) is null)
        {
            return false;
        }

        var value = skipped ? 0 : CheckinOn(habitId, day)?.Value ?? 0;
        Write(habitId, day, value, skipped);
        return true;
    }

    /// <summary>Pauses a habit from <paramref name="from"/> until it resumes; nothing when it is already paused then.</summary>
    public bool Pause(string habitId, DateOnly from)
    {
        if (Find(habitId) is null || OpenPause(habitId, from) is not null)
        {
            return false;
        }

        var row = rows.Create(PauseTable, new Dictionary<string, JsonNode?>
        {
            ["habit_id"] = habitId,
            ["starts_on"] = from.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture),
            ["ends_on"] = null,
        });
        if (row is null)
        {
            return false;
        }

        replica.Queue(PauseTable, row);
        requestSync();
        return true;
    }

    /// <summary>
    /// Resumes a habit on <paramref name="day"/>: the pause covering it ends the day before, or goes away
    /// when it started that day. The pause stays otherwise, so old streaks still read right.
    /// </summary>
    public bool Resume(string habitId, DateOnly day)
    {
        if (OpenPause(habitId, day) is not { } pause)
        {
            return false;
        }

        return pause.From >= day
            ? Change(PauseTable, pause.Id, row => row[SyncedTable.DeletedAt] = rows.Timestamp())
            : Change(PauseTable, pause.Id, row => row["ends_on"] = day.AddDays(-1).ToString("yyyy-MM-dd", CultureInfo.InvariantCulture));
    }

    // The pause covering the day, or one that starts later and has no end.
    private HabitPause? OpenPause(string habitId, DateOnly day) => Pauses()
        .Where(pause => pause.HabitId == habitId)
        .FirstOrDefault(pause => (pause.From <= day && (pause.Until is null || pause.Until >= day)) || (pause.From > day && pause.Until is null));

    private HabitCheckin? CheckinOn(string habitId, DateOnly day) =>
        replica.Get(CheckinTable, HabitRules.CheckinId(habitId, day)) is { } row && ToCheckin(row) is { Deleted: false } checkin ? checkin : null;

    private void Write(string habitId, DateOnly day, double value, bool skipped)
    {
        var id = HabitRules.CheckinId(habitId, day);
        var existing = replica.Get(CheckinTable, id);
        if (existing is not null)
        {
            existing["value"] = value;
            existing["skipped"] = skipped;
            existing[SyncedTable.DeletedAt] = null;
            replica.Queue(CheckinTable, existing);
            requestSync();
            return;
        }

        var row = rows.Create(CheckinTable, new Dictionary<string, JsonNode?>
        {
            [SyncedTable.Id] = id,
            ["habit_id"] = habitId,
            ["day"] = day.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture),
            ["value"] = value,
            ["skipped"] = skipped,
        });
        if (row is null)
        {
            return;
        }

        replica.Queue(CheckinTable, row);
        requestSync();
    }

    // The draft as the server will take it, or null: a name, a cadence with the days it needs, and a
    // positive target for a count or an amount (supabase/migrations/0010_habits.sql).
    private HabitDraft? Check(HabitDraft draft)
    {
        if (Clip(draft.Name, MaxName) is not { } name)
        {
            return null;
        }

        var weekdays = draft.Cadence == HabitRules.OnWeekdays ? draft.Weekdays : null;
        var times = draft.Cadence is HabitRules.PerWeek or HabitRules.PerMonth ? draft.Times : null;
        var fits = draft.Cadence switch
        {
            HabitRules.Daily => true,
            HabitRules.OnWeekdays => weekdays is >= 1 and <= 127,
            HabitRules.PerWeek => times is >= 1 and <= 7,
            HabitRules.PerMonth => times is >= 1 and <= 31,
            _ => false,
        };
        if (!fits || draft.Measure is not (HabitRules.Check or HabitRules.Count or HabitRules.Amount))
        {
            return null;
        }

        var counted = draft.Measure != HabitRules.Check;
        var target = counted ? draft.Target : null;
        if (counted && (target is not { } value || value <= 0 || !double.IsFinite(value)))
        {
            return null;
        }

        return draft with
        {
            Name = name,
            Weekdays = weekdays,
            Times = times,
            Target = target,
            Unit = counted ? Clip(draft.Unit, MaxUnit) : null,
            Emoji = Clip(draft.Emoji, MaxEmoji),
            GoalId = string.IsNullOrEmpty(draft.GoalId) ? null : draft.GoalId,
        };
    }

    // Trimmed and cut to length; null when nothing is left.
    private static string? Clip(string? text, int length) => text?.Trim() is { Length: > 0 } trimmed
        ? trimmed.Length > length ? trimmed[..length] : trimmed
        : null;

    private static Dictionary<string, JsonNode?> Values(HabitDraft draft) => new()
    {
        ["name"] = draft.Name,
        ["emoji"] = draft.Emoji,
        ["cadence"] = draft.Cadence,
        ["weekdays"] = draft.Weekdays,
        ["times"] = draft.Times,
        ["measure"] = draft.Measure,
        ["target"] = draft.Target,
        ["unit"] = draft.Unit,
        ["goal_id"] = draft.GoalId,
    };

    private static double? Number(JsonNode? node) => node is JsonValue value
        ? value.TryGetValue<double>(out var number) ? number
        : value.TryGetValue<long>(out var whole) ? whole
        : value.TryGetValue<int>(out var small) ? small
        : null
        : null;

    private static int? Count(JsonNode? node) => Number(node) is { } number ? (int)number : null;

    private static bool Flag(JsonNode? node) => node is JsonValue value
        && (value.TryGetValue<bool>(out var flag) ? flag : value.TryGetValue<long>(out var number) && number != 0);

    private static DateOnly Day(JsonNode? node) =>
        (string?)node is { } text ? DateOnly.ParseExact(text, "yyyy-MM-dd", CultureInfo.InvariantCulture) : DateOnly.MinValue;

    private static HabitItem ToItem(JsonObject row) => new(
        (string?)row[SyncedTable.Id] ?? string.Empty,
        (string?)row["name"] ?? string.Empty,
        Day(row["starts_on"]))
    {
        Cadence = (string?)row["cadence"] ?? HabitRules.Daily,
        Weekdays = Count(row["weekdays"]),
        Times = Count(row["times"]),
        Measure = (string?)row["measure"] ?? HabitRules.Check,
        Target = Number(row["target"]),
        Unit = (string?)row["unit"],
        Emoji = (string?)row["emoji"],
        GoalId = (string?)row["goal_id"],
        Archived = row["archived_at"] is not null,
        Position = Number(row["position"]) ?? 0,
        Deleted = row[SyncedTable.DeletedAt] is not null,
    };

    private static HabitCheckin ToCheckin(JsonObject row) => new(
        (string?)row[SyncedTable.Id] ?? string.Empty,
        (string?)row["habit_id"] ?? string.Empty,
        Day(row["day"]),
        Number(row["value"]) ?? 0,
        Flag(row["skipped"]),
        row[SyncedTable.DeletedAt] is not null);

    private static HabitPause ToPause(JsonObject row) => new(
        (string?)row[SyncedTable.Id] ?? string.Empty,
        (string?)row["habit_id"] ?? string.Empty,
        Day(row["starts_on"]),
        (string?)row["ends_on"] is { } ends ? DateOnly.ParseExact(ends, "yyyy-MM-dd", CultureInfo.InvariantCulture) : null,
        row[SyncedTable.DeletedAt] is not null);

    private bool Change(string table, string id, Action<JsonObject> edit)
    {
        if (replica.Get(table, id) is not { } row || row[SyncedTable.DeletedAt] is not null)
        {
            return false;
        }

        edit(row);
        replica.Queue(table, row);
        requestSync();
        return true;
    }
}
