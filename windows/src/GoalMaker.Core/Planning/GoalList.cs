using System.Globalization;
using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Planning;

/// <summary>
/// The owner's goals and the amounts logged on them, read from the replica and changed through its
/// outbox (docs/goals.md). Every write asks for a sync.
/// </summary>
public sealed class GoalList
{
    private const string Table = "goals";
    private const string EntryTable = "goal_entries";
    private const int MaxTitle = 200;
    private const int MaxEmoji = 16;
    private const int MaxUnit = 20;
    private readonly IReplica replica;
    private readonly NewRows rows;
    private readonly Action requestSync;

    public GoalList(IReplica replica, NewRows rows, Action requestSync)
    {
        this.replica = replica;
        this.rows = rows;
        this.requestSync = requestSync;
        replica.Changed += (_, table) =>
        {
            if (table is Table or EntryTable)
            {
                Changed?.Invoke(this, EventArgs.Empty);
            }
        };
    }

    /// <summary>Raised after every change to the goals or the amounts logged on them.</summary>
    public event EventHandler? Changed;

    /// <summary>Every goal that isn't deleted, longest period first, then by period, position and title.</summary>
    public IReadOnlyList<GoalItem> All() =>
        [.. replica.All(Table).Select(ToItem).Where(goal => !goal.Deleted)
            .OrderByDescending(goal => goal.Horizon)
            .ThenBy(goal => goal.PeriodStart)
            .ThenBy(goal => goal.Position)
            .ThenBy(goal => goal.Title, StringComparer.OrdinalIgnoreCase)];

    public GoalItem? Find(string id) => replica.Get(Table, id) is { } row && ToItem(row) is { Deleted: false } goal ? goal : null;

    /// <summary>The amounts logged on every goal, not deleted.</summary>
    public IReadOnlyList<GoalEntryItem> Entries() => [.. replica.All(EntryTable).Select(ToEntry).Where(entry => !entry.Deleted)];

    /// <summary>Adds a goal. Null when the draft isn't one the server would keep.</summary>
    public GoalItem? Add(GoalDraft draft)
    {
        if (Check(draft) is not { } clean)
        {
            return null;
        }

        var values = Values(clean);
        values["status"] = GoalRules.Open;
        values["position"] = (double)All().Count(goal => goal.Horizon == clean.Horizon && goal.PeriodStart == clean.PeriodStart);
        if (rows.Create(Table, values) is not { } row)
        {
            return null;
        }

        replica.Queue(Table, row);
        requestSync();
        return ToItem(row);
    }

    /// <summary>Changes a goal to what <paramref name="draft"/> says. False when the draft isn't valid or the goal is gone.</summary>
    public bool Update(string id, GoalDraft draft) => Check(draft) is { } clean && Change(id, row =>
    {
        foreach (var (column, value) in Values(clean))
        {
            row[column] = value;
        }
    });

    /// <summary>Open, done (with the time) or dropped.</summary>
    public bool SetStatus(string id, string status) =>
        status is GoalRules.Open or GoalRules.Done or GoalRules.Dropped && Change(id, row =>
        {
            row["status"] = status;
            row["completed_at"] = status == GoalRules.Done ? rows.Timestamp() : null;
        });

    /// <summary>Deletes a goal softly. Tasks and child goals that pointed at it just have no goal from then on.</summary>
    public bool Delete(string id) => Change(id, row => row[SyncedTable.DeletedAt] = rows.Timestamp());

    /// <summary>Logs <paramref name="amount"/> on a numeric goal for <paramref name="day"/> ("+5 km"); a negative amount corrects. Null for zero.</summary>
    public GoalEntryItem? LogAmount(string goalId, DateOnly day, double amount)
    {
        if (amount == 0 || !double.IsFinite(amount) || Find(goalId) is null)
        {
            return null;
        }

        var row = rows.Create(EntryTable, new Dictionary<string, JsonNode?>
        {
            ["goal_id"] = goalId,
            ["day"] = day.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture),
            ["amount"] = amount,
        });
        if (row is null)
        {
            return null;
        }

        replica.Queue(EntryTable, row);
        requestSync();
        return ToEntry(row);
    }

    /// <summary>
    /// Copies the goals of the <paramref name="horizon"/> period before the one starting on
    /// <paramref name="start"/> into it (docs/goals.md) and returns how many were made. Nothing when the
    /// period already has goals.
    /// </summary>
    public int CopyPrevious(GoalHorizon horizon, DateOnly start)
    {
        var goals = All();
        if (goals.Any(goal => goal.Horizon == horizon && goal.PeriodStart == start))
        {
            return 0;
        }

        var previous = GoalRules.PeriodStart(horizon, start.AddDays(-1));
        var copies = GoalRules.Copies(
            goals.Where(goal => goal.Horizon == horizon && goal.PeriodStart == previous),
            goals.ToDictionary(goal => goal.Id, StringComparer.Ordinal),
            horizon,
            start);
        replica.InTransaction(() =>
        {
            foreach (var copy in copies)
            {
                Add(new GoalDraft(copy.Title, horizon, start, copy.Mode, copy.Emoji, copy.ParentId, copy.Target, copy.Unit));
            }
        });
        return copies.Count;
    }

    // The draft as the server will take it, or null: a title, a numeric goal with a positive target.
    private GoalDraft? Check(GoalDraft draft)
    {
        var title = Clip(draft.Title, MaxTitle);
        if (title is null || draft.Mode is not (GoalRules.ModeDone or GoalRules.ModeTasks or GoalRules.ModeNumber))
        {
            return null;
        }

        var number = draft.Mode == GoalRules.ModeNumber;
        var target = number ? draft.Target : null;
        if (number && (target is not { } value || value <= 0 || !double.IsFinite(value)))
        {
            return null;
        }

        var start = GoalRules.PeriodStart(draft.Horizon, draft.PeriodStart);
        var parent = draft.ParentId is { } id && Find(id) is { } found && GoalRules.CanServe(draft.Horizon, start, found.Horizon, found.PeriodStart)
            ? found.Id
            : null;
        return draft with
        {
            Title = title,
            PeriodStart = start,
            Emoji = Clip(draft.Emoji, MaxEmoji),
            ParentId = parent,
            Target = target,
            Unit = number ? Clip(draft.Unit, MaxUnit) : null,
        };
    }

    // Trimmed and cut to length; null when nothing is left.
    private static string? Clip(string? text, int length) => text?.Trim() is { Length: > 0 } trimmed
        ? trimmed.Length > length ? trimmed[..length] : trimmed
        : null;

    private static Dictionary<string, JsonNode?> Values(GoalDraft draft) => new()
    {
        ["title"] = draft.Title,
        ["emoji"] = draft.Emoji,
        ["horizon"] = GoalRules.Id(draft.Horizon),
        ["period_start"] = draft.PeriodStart.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture),
        ["parent_id"] = draft.ParentId,
        ["progress_mode"] = draft.Mode,
        ["target"] = draft.Target,
        ["unit"] = draft.Unit,
    };

    private static double? Number(JsonNode? node) => node is JsonValue value
        ? value.TryGetValue<double>(out var number) ? number : value.TryGetValue<long>(out var whole) ? whole : null
        : null;

    private static DateOnly Day(JsonNode? node) =>
        (string?)node is { } text ? DateOnly.ParseExact(text, "yyyy-MM-dd", CultureInfo.InvariantCulture) : DateOnly.MinValue;

    private static GoalItem ToItem(JsonObject row) => new(
        (string?)row[SyncedTable.Id] ?? string.Empty,
        (string?)row["title"] ?? string.Empty,
        GoalRules.HorizonOf((string?)row["horizon"]) ?? GoalHorizon.Week,
        Day(row["period_start"]))
    {
        Mode = (string?)row["progress_mode"] ?? GoalRules.ModeDone,
        Status = (string?)row["status"] ?? GoalRules.Open,
        Emoji = (string?)row["emoji"],
        ParentId = (string?)row["parent_id"],
        Target = Number(row["target"]),
        Unit = (string?)row["unit"],
        CompletedAt = (string?)row["completed_at"],
        Position = Number(row["position"]) ?? 0,
        Deleted = row[SyncedTable.DeletedAt] is not null,
    };

    private static GoalEntryItem ToEntry(JsonObject row) => new(
        (string?)row[SyncedTable.Id] ?? string.Empty,
        (string?)row["goal_id"] ?? string.Empty,
        Day(row["day"]),
        Number(row["amount"]) ?? 0,
        row[SyncedTable.DeletedAt] is not null);

    private bool Change(string id, Action<JsonObject> edit)
    {
        if (replica.Get(Table, id) is not { } row || row[SyncedTable.DeletedAt] is not null)
        {
            return false;
        }

        edit(row);
        replica.Queue(Table, row);
        requestSync();
        return true;
    }
}
