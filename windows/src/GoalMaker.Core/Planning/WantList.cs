using System.Globalization;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Planning;

/// <summary>
/// The owner's wants and their cooldown thresholds, read from the replica and changed through its
/// outbox (docs/wants.md). A new want takes its cooldown from <see cref="WantRules"/> on the planning
/// day. Every write asks for a sync.
/// </summary>
public sealed partial class WantList
{
    private const string Table = "wants";
    private const string CooldownsTable = "want_cooldowns";
    private const int MaxTitle = 200;
    private const int MaxReason = 2000;
    private const int MaxLink = 2000;
    private const int MaxNote = 2000;
    private const double MaxPrice = 100_000_000;
    private readonly IReplica replica;
    private readonly NewRows rows;
    private readonly Action requestSync;
    private readonly Func<DateOnly> today;

    public WantList(IReplica replica, NewRows rows, Action requestSync, Func<DateOnly> today)
    {
        this.replica = replica;
        this.rows = rows;
        this.requestSync = requestSync;
        this.today = today;
        replica.Changed += (_, table) =>
        {
            if (table is Table or CooldownsTable)
            {
                Changed?.Invoke(this, EventArgs.Empty);
            }
        };
    }

    /// <summary>Raised after every change to a want or the thresholds.</summary>
    public event EventHandler? Changed;

    /// <summary>Every want that isn't deleted, soonest to cool first, then by title.</summary>
    public IReadOnlyList<WantItem> All() =>
        [.. replica.All(Table).Select(ToItem).Where(want => !want.Deleted)
            .OrderBy(want => want.CoolsUntil)
            .ThenBy(want => want.Title, StringComparer.OrdinalIgnoreCase)];

    public WantItem? Get(string id) =>
        replica.Get(Table, id) is { } row && ToItem(row) is { Deleted: false } want ? want : null;

    /// <summary>The owner's thresholds, or the defaults while they have none.</summary>
    public WantCooldowns Cooldowns()
    {
        if (rows.Owner() is not { } owner
            || replica.Get(CooldownsTable, WantRules.CooldownsId(owner)) is not { } row
            || row[SyncedTable.DeletedAt] is not null)
        {
            return WantCooldowns.Default;
        }

        var fallback = WantCooldowns.Default;
        return new WantCooldowns(
            Number(row["small_under"]) ?? fallback.SmallUnder,
            Whole(row["small_days"]) ?? fallback.SmallDays,
            Number(row["medium_under"]) ?? fallback.MediumUnder,
            Whole(row["medium_days"]) ?? fallback.MediumDays,
            Whole(row["large_days"]) ?? fallback.LargeDays,
            Whole(row["unpriced_days"]) ?? fallback.UnpricedDays,
            (string?)row["currency"] ?? fallback.Currency);
    }

    /// <summary>
    /// Keeps new thresholds; false when they don't hold together (days 0 to 365, amounts not negative,
    /// the medium threshold not below the small one, a three-letter currency). Wants already cooling keep their days.
    /// </summary>
    public bool SetCooldowns(WantCooldowns value)
    {
        var currency = value.Currency.Trim().ToUpperInvariant();
        int[] days = [value.SmallDays, value.MediumDays, value.LargeDays, value.UnpricedDays];
        if (days.Any(day => day is < 0 or > WantRules.MaxDays) || value.SmallUnder < 0 || value.MediumUnder < value.SmallUnder
            || !Currency().IsMatch(currency) || rows.Owner() is not { } owner)
        {
            return false;
        }

        var fields = new Dictionary<string, JsonNode?>
        {
            ["small_under"] = value.SmallUnder,
            ["small_days"] = value.SmallDays,
            ["medium_under"] = value.MediumUnder,
            ["medium_days"] = value.MediumDays,
            ["large_days"] = value.LargeDays,
            ["unpriced_days"] = value.UnpricedDays,
            ["currency"] = currency,
        };
        var id = WantRules.CooldownsId(owner);
        JsonObject? row;
        if (replica.Get(CooldownsTable, id) is { } existing)
        {
            foreach (var (column, field) in fields)
            {
                existing[column] = field;
            }

            existing[SyncedTable.DeletedAt] = null;
            row = existing;
        }
        else
        {
            fields[SyncedTable.Id] = id;
            row = rows.Create(CooldownsTable, fields);
        }

        if (row is null)
        {
            return false;
        }

        replica.Queue(CooldownsTable, row);
        requestSync();
        return true;
    }

    /// <summary>
    /// Adds a want with the cooldown its price or the owner's pick gives, or a need with none. Null
    /// without a title, or a want without a reason.
    /// </summary>
    public WantItem? Add(WantDraft draft)
    {
        if (Check(draft) is not { } clean)
        {
            return null;
        }

        var addedOn = today();
        var days = WantRules.CooldownDays(clean.Price, clean.Currency, Cooldowns(), clean.PickedDays, clean.Kind);
        var values = Values(clean);
        values["cooldown_days"] = days;
        values["added_on"] = addedOn.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
        values["cools_until"] = WantRules.CoolsUntil(addedOn, days).ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
        values["made_by"] = ProjectRules.Owner;
        values["kind"] = clean.Kind;
        values["decision_note"] = string.Empty;
        values["checked_note"] = string.Empty;
        if (rows.Create(Table, values) is not { } row)
        {
            return null;
        }

        replica.Queue(Table, row);
        requestSync();
        return ToItem(row);
    }

    /// <summary>Changes what the want is; its cooldown stays as it was set.</summary>
    public bool Update(string id, WantDraft draft) => Check(draft) is { } clean && Change(id, row =>
    {
        foreach (var (column, value) in Values(clean))
        {
            row[column] = value;
        }
    });

    /// <summary>Marks a want bought or dropped, with an optional note.</summary>
    public bool Decide(string id, string decision, string note = "") =>
        decision is WantRules.Bought or WantRules.Dropped && Change(id, row =>
        {
            row["decision"] = decision;
            row["decided_at"] = rows.Timestamp();
            row["decision_note"] = Clip(note, MaxNote) ?? string.Empty;
        });

    /// <summary>Takes a decision back; the want is cooling or ready again, as its day says.</summary>
    public bool Reopen(string id) => Change(id, row =>
    {
        row["decision"] = null;
        row["decided_at"] = null;
        row["decision_note"] = string.Empty;
    });

    public bool Delete(string id) => Change(id, row => row[SyncedTable.DeletedAt] = rows.Timestamp());

    /// <summary>Brings a deleted want back, as the undo after a delete does.</summary>
    public bool Restore(string id)
    {
        if (replica.Get(Table, id) is not { } row || row[SyncedTable.DeletedAt] is null)
        {
            return false;
        }

        row[SyncedTable.DeletedAt] = null;
        replica.Queue(Table, row);
        requestSync();
        return true;
    }

    [GeneratedRegex("^[A-Z]{3}$")]
    private static partial Regex Currency();

    private static string? Clip(string? text, int length)
    {
        var trimmed = text?.Trim() ?? string.Empty;
        if (trimmed.Length > length)
        {
            trimmed = trimmed[..length];
        }

        return trimmed.Length == 0 ? null : trimmed;
    }

    private static WantDraft? Check(WantDraft draft)
    {
        var need = draft.Kind == WantRules.Need;
        var reason = Clip(draft.Reason, MaxReason);

        // A want says why it is wanted; a need may leave it out (supabase/migrations/0025_wants_needs.sql).
        if (Clip(draft.Title, MaxTitle) is not { } title || (reason is null && !need))
        {
            return null;
        }

        var currency = draft.Currency.Trim().ToUpperInvariant();
        return draft with
        {
            Title = title,
            Reason = reason ?? string.Empty,
            Link = Clip(draft.Link, MaxLink),
            Price = draft.Price is >= 0 and <= MaxPrice ? draft.Price : null,
            Currency = Currency().IsMatch(currency) ? currency : WantCooldowns.Default.Currency,
            Kind = need ? WantRules.Need : WantRules.Want,
            NeedBy = need ? draft.NeedBy : null,
        };
    }

    private static Dictionary<string, JsonNode?> Values(WantDraft draft) => new()
    {
        ["title"] = draft.Title,
        ["reason"] = draft.Reason,
        ["link"] = draft.Link,
        ["price"] = draft.Price,
        ["currency"] = draft.Currency,
        ["area_id"] = draft.AreaId,
        ["need_by"] = draft.NeedBy?.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture),
    };

    private static WantItem ToItem(JsonObject row) => new(
        (string?)row[SyncedTable.Id] ?? string.Empty,
        (string?)row["title"] ?? string.Empty,
        (string?)row["reason"] ?? string.Empty,
        Whole(row["cooldown_days"]) ?? 0,
        Day(row["added_on"]),
        Day(row["cools_until"]),
        Link: (string?)row["link"],
        Price: Number(row["price"]),
        Currency: (string?)row["currency"] ?? WantCooldowns.Default.Currency,
        AreaId: (string?)row["area_id"],
        Decision: (string?)row["decision"],
        DecidedAt: (string?)row["decided_at"],
        DecisionNote: (string?)row["decision_note"] ?? string.Empty,
        CheckedPrice: Number(row["checked_price"]),
        CheckedAt: (string?)row["checked_at"],
        CheckedNote: (string?)row["checked_note"] ?? string.Empty,
        MadeBy: (string?)row["made_by"] ?? ProjectRules.Owner,
        Deleted: row[SyncedTable.DeletedAt] is not null,
        Kind: (string?)row["kind"] ?? WantRules.Want,
        NeedBy: (string?)row["need_by"] is { } needBy ? DateOnly.ParseExact(needBy, "yyyy-MM-dd", CultureInfo.InvariantCulture) : null);

    private static double? Number(JsonNode? node) => node is JsonValue value
        ? value.TryGetValue<double>(out var number) ? number
            : value.TryGetValue<long>(out var whole) ? whole
            : value.TryGetValue<int>(out var small) ? small
            : null
        : null;

    private static int? Whole(JsonNode? node) => Number(node) is { } number ? (int)number : null;

    private static DateOnly Day(JsonNode? node) =>
        (string?)node is { } text ? DateOnly.ParseExact(text, "yyyy-MM-dd", CultureInfo.InvariantCulture) : DateOnly.MinValue;

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
