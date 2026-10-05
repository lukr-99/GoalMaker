using System.Globalization;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Planning;

/// <summary>
/// Tally's synced rows, read from the replica and changed through its outbox (docs/tally.md): this
/// PC's daily totals, which it rewrites a planning day at a time, and the owner's own categories and
/// rules. <c>device</c> is this install's id; the raw record of windows never reaches the replica.
/// Every write asks for a sync.
/// </summary>
public sealed partial class TallyList
{
    private const string DaysTable = "tally_days";
    private const string CategoriesTable = "tally_categories";
    private const string RulesTable = "tally_rules";
    private const int MaxName = 40;
    private const int MaxEmoji = 16;
    private const int MaxPattern = 200;
    private const int MaxCategory = 60;
    private readonly IReplica replica;
    private readonly NewRows rows;
    private readonly Func<string> device;
    private readonly Action requestSync;

    public TallyList(IReplica replica, NewRows rows, Func<string> device, Action requestSync)
    {
        this.replica = replica;
        this.rows = rows;
        this.device = device;
        this.requestSync = requestSync;
        replica.Changed += (_, table) =>
        {
            if (table is DaysTable or CategoriesTable or RulesTable)
            {
                Changed?.Invoke(this, EventArgs.Empty);
            }
        };
    }

    /// <summary>Raised after every change to a tally day, category or rule.</summary>
    public event EventHandler? Changed;

    /// <summary>
    /// Makes <paramref name="totals"/> this PC's rows for <paramref name="day"/>: each total goes in the
    /// row its id names, and this PC's other rows for the day are deleted. Other devices' rows and
    /// totals of other days are left alone. False when nobody is signed in.
    /// </summary>
    public bool RewriteDay(DateOnly day, IEnumerable<TallyTotal> totals)
    {
        if (rows.Owner() is not { } owner)
        {
            return false;
        }

        var me = device();
        var text = day.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
        var kept = new HashSet<string>(StringComparer.Ordinal);
        var changed = false;
        foreach (var total in totals.Where(total => total.Day == day && total.Minutes > 0))
        {
            var id = TallyRules.DayId(owner, day, me, total.Category, total.Project);
            var minutes = Math.Min(total.Minutes, TallyRules.MaxMinutes);
            kept.Add(id);
            JsonObject? row;
            if (replica.Get(DaysTable, id) is { } existing)
            {
                if (existing[SyncedTable.DeletedAt] is null && Whole(existing["minutes"]) == minutes)
                {
                    continue;
                }

                existing["minutes"] = minutes;
                existing[SyncedTable.DeletedAt] = null;
                row = existing;
            }
            else
            {
                row = rows.Create(DaysTable, new Dictionary<string, JsonNode?>
                {
                    [SyncedTable.Id] = id,
                    ["day"] = text,
                    ["device"] = me,
                    ["device_kind"] = TallyRules.Pc,
                    ["category"] = total.Category,
                    ["project_id"] = total.Project,
                    ["minutes"] = minutes,
                });
            }

            if (row is not null)
            {
                replica.Queue(DaysTable, row);
                changed = true;
            }
        }

        foreach (var row in replica.All(DaysTable))
        {
            if ((string?)row["day"] == text && (string?)row["device"] == me && row[SyncedTable.DeletedAt] is null
                && !kept.Contains((string?)row[SyncedTable.Id] ?? string.Empty))
            {
                row[SyncedTable.DeletedAt] = rows.Timestamp();
                replica.Queue(DaysTable, row);
                changed = true;
            }
        }

        if (changed)
        {
            requestSync();
        }

        return true;
    }

    /// <summary>Every device's totals from <paramref name="from"/> to <paramref name="to"/>, by day, category, project (none first) and device.</summary>
    public IReadOnlyList<TallyDay> Days(DateOnly from, DateOnly to) =>
        [.. replica.All(DaysTable)
            .Where(row => row[SyncedTable.DeletedAt] is null)
            .Select(ToDay)
            .Where(day => day.Day >= from && day.Day <= to)
            .OrderBy(day => day.Day)
            .ThenBy(day => day.Category, StringComparer.Ordinal)
            .ThenBy(day => day.ProjectId ?? string.Empty, StringComparer.Ordinal)
            .ThenBy(day => day.Device, StringComparer.Ordinal)];

    /// <summary>The owner's own categories, in their order.</summary>
    public IReadOnlyList<TallyCategory> Categories() =>
        [.. Live(CategoriesTable).Select(row => new TallyCategory(
            (string?)row[SyncedTable.Id] ?? string.Empty,
            (string?)row["name"] ?? string.Empty,
            (string?)row["color"] ?? "violet",
            (string?)row["emoji"]))];

    /// <summary>Adds one of the owner's own categories at the end. Null without a name or with a color that isn't a palette name.</summary>
    public TallyCategory? AddCategory(string name, string color, string? emoji = null)
    {
        if (CategoryValues(name, color, emoji) is not { } values)
        {
            return null;
        }

        values["position"] = NextPosition(CategoriesTable);
        if (rows.Create(CategoriesTable, values) is not { } row)
        {
            return null;
        }

        replica.Queue(CategoriesTable, row);
        requestSync();
        return new TallyCategory((string)row[SyncedTable.Id]!, (string)values["name"]!, (string)values["color"]!, (string?)values["emoji"]);
    }

    /// <summary>Renames, recolors or re-marks one of the owner's categories. False for the same reasons adding one is refused, or an unknown id.</summary>
    public bool UpdateCategory(string id, string name, string color, string? emoji = null) =>
        CategoryValues(name, color, emoji) is { } values && Change(CategoriesTable, id, row =>
        {
            foreach (var (column, value) in values)
            {
                row[column] = value;
            }
        });

    /// <summary>
    /// Deletes one of the owner's categories. Its rules and the time already sorted into it stay; the
    /// places name such time as a category that is gone.
    /// </summary>
    public bool DeleteCategory(string id) => Change(CategoriesTable, id, row => row[SyncedTable.DeletedAt] = rows.Timestamp());

    /// <summary>The owner's own rules, in the order they are tried.</summary>
    public IReadOnlyList<TallyRule> Rules() =>
        [.. Live(RulesTable).Select(row => new TallyRule(
            (string?)row["match"] ?? TallyRules.App,
            (string?)row["pattern"] ?? string.Empty,
            (string?)row["platform"] ?? TallyRules.Any,
            (string?)row["category"] ?? TallyRules.Other,
            (string?)row["project_id"],
            (string?)row[SyncedTable.Id]))];

    /// <summary>
    /// Adds one of the owner's own rules after the others. Null without a pattern or a category, with
    /// an unknown match or platform, or for a title or folder rule on Android, which has neither.
    /// </summary>
    public TallyRule? AddRule(TallyRule rule)
    {
        if (RuleValues(rule) is not { } values)
        {
            return null;
        }

        values["position"] = NextPosition(RulesTable);
        if (rows.Create(RulesTable, values) is not { } row)
        {
            return null;
        }

        replica.Queue(RulesTable, row);
        requestSync();
        return rule with { Pattern = (string)values["pattern"]!, Category = (string)values["category"]!, Id = (string?)row[SyncedTable.Id] };
    }

    /// <summary>Changes one of the owner's rules in place, keeping its turn. False for the same reasons adding one is refused, or an unknown id.</summary>
    public bool UpdateRule(string id, TallyRule rule) =>
        RuleValues(rule) is { } values && Change(RulesTable, id, row =>
        {
            foreach (var (column, value) in values)
            {
                row[column] = value;
            }
        });

    public bool DeleteRule(string id) => Change(RulesTable, id, row => row[SyncedTable.DeletedAt] = rows.Timestamp());

    /// <summary>
    /// Sorts one app, site or folder into <paramref name="category"/> in one step (docs/tally.md): the
    /// owner's own rule for exactly that <paramref name="match"/> and <paramref name="pattern"/> (ignoring
    /// case) on that <paramref name="platform"/> changes its category, keeping its pattern and project,
    /// or a new rule is added after the others. False when the rule wouldn't hold.
    /// </summary>
    public bool SortInto(string match, string pattern, string platform, string category)
    {
        var clean = pattern.Trim();
        var same = Rules().FirstOrDefault(rule =>
            rule.Match == match && rule.Platform == platform && string.Equals(rule.Pattern, clean, StringComparison.OrdinalIgnoreCase));
        return same?.Id is { } id
            ? UpdateRule(id, same with { Category = category })
            : AddRule(new TallyRule(match, clean, platform, category)) is not null;
    }

    /// <summary>
    /// Merges the owner's own category <paramref name="from"/> into <paramref name="into"/>: its rules
    /// sort into <paramref name="into"/> from now on, and <paramref name="from"/> is deleted. Days already
    /// counted keep the category they were counted in. False when <paramref name="from"/> isn't one of
    /// the owner's categories or the two are the same.
    /// </summary>
    public bool MergeCategory(string from, string into)
    {
        if (from == into || Categories().All(category => category.Id != from))
        {
            return false;
        }

        foreach (var rule in Rules().Where(rule => rule.Category == from && rule.Id is not null))
        {
            UpdateRule(rule.Id!, rule with { Category = into });
        }

        return DeleteCategory(from);
    }

    [GeneratedRegex("^[a-z][a-z0-9-]{0,23}$")]
    private static partial Regex Palette();

    private static string? Clip(string? text, int length)
    {
        var trimmed = text?.Trim() ?? string.Empty;
        if (trimmed.Length > length)
        {
            trimmed = trimmed[..length];
        }

        return trimmed.Length == 0 ? null : trimmed;
    }

    private static TallyDay ToDay(JsonObject row) => new(
        (string?)row[SyncedTable.Id] ?? string.Empty,
        (string?)row["day"] is { } text ? DateOnly.ParseExact(text, "yyyy-MM-dd", CultureInfo.InvariantCulture) : DateOnly.MinValue,
        (string?)row["device"] ?? string.Empty,
        (string?)row["device_kind"] ?? TallyRules.Pc,
        (string?)row["category"] ?? TallyRules.Other,
        (string?)row["project_id"],
        Whole(row["minutes"]) ?? 0);

    private static int? Whole(JsonNode? node) => node is JsonValue value
        ? value.TryGetValue<long>(out var whole) ? (int)whole
            : value.TryGetValue<int>(out var small) ? small
            : value.TryGetValue<double>(out var number) ? (int)number
            : null
        : null;

    // A category's columns, or null for no name or a color that isn't a palette name.
    private static Dictionary<string, JsonNode?>? CategoryValues(string name, string color, string? emoji)
    {
        var clean = Clip(name, MaxName);
        var palette = color.Trim().ToLowerInvariant();
        if (clean is null || !Palette().IsMatch(palette))
        {
            return null;
        }

        return new Dictionary<string, JsonNode?>
        {
            ["name"] = clean,
            ["color"] = palette,
            ["emoji"] = emoji?.Trim() is { Length: > 0 and <= MaxEmoji } trimmed ? trimmed : null,
        };
    }

    // A rule's columns, or null for a rule the server would refuse.
    private static Dictionary<string, JsonNode?>? RuleValues(TallyRule rule)
    {
        var pattern = Clip(rule.Pattern, MaxPattern);
        var category = Clip(rule.Category, MaxCategory);
        if (pattern is null || category is null
            || rule.Match is not (TallyRules.App or TallyRules.Title or TallyRules.Folder)
            || rule.Platform is not (TallyRules.Android or TallyRules.Windows or TallyRules.Any)
            || (rule.Match != TallyRules.App && rule.Platform == TallyRules.Android))
        {
            return null;
        }

        return new Dictionary<string, JsonNode?>
        {
            ["match"] = rule.Match,
            ["pattern"] = pattern,
            ["platform"] = rule.Platform,
            ["category"] = category,
            ["project_id"] = rule.Project,
        };
    }

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

    // A table's rows that aren't deleted, by position, then by when they were made.
    private IEnumerable<JsonObject> Live(string table) =>
        replica.All(table)
            .Where(row => row[SyncedTable.DeletedAt] is null)
            .OrderBy(row => Whole(row["position"]) ?? 0)
            .ThenBy(row => (string?)row[SyncedTable.CreatedAt], StringComparer.Ordinal);

    private int NextPosition(string table) => Live(table).Select(row => Whole(row["position"]) ?? 0).DefaultIfEmpty(-1).Max() + 1;
}
