using System.Globalization;
using System.Text;

namespace GoalMaker.Core.Planning;

/// <summary>
/// The board of a project (docs/projects.md, contracts/vectors/projects.json): where a new item lands,
/// how a column and the task's own state move together, the order items sit in, and which items the
/// who-made-it switch shows, and the short ids its items read by (GM-12).
/// </summary>
public static class ProjectRules
{
    public const string Backlog = "backlog";
    public const string Todo = "todo";
    public const string Doing = "doing";
    public const string Done = "done";

    /// <summary>The board's last column: every dropped item, whatever column it is stored in (docs/projects.md).</summary>
    public const string Dropped = "dropped";

    public const string Task = "task";
    public const string Idea = "idea";
    public const string Bug = "bug";

    public const string Low = "low";
    public const string Normal = "normal";
    public const string High = "high";
    public const string Urgent = "urgent";

    public const string Active = "active";
    public const string Paused = "paused";
    public const string Finished = "done";

    public const string Owner = "owner";
    public const string Claude = "claude";
    public const string Everyone = "all";

    /// <summary>The shortest and the longest a project's key can be (docs/projects.md, "Item ids").</summary>
    public const int ShortestItemKey = 2;

    public const int LongestItemKey = 6;

    // A suggested key has at most this many characters before a number is added for a taken one.
    private const int LongestSuggestedKey = 4;

    /// <summary>How many days a done item stays on a new project's board (supabase/migrations/0018_board_archive.sql).</summary>
    public const int DefaultArchiveAfterDays = 14;

    /// <summary>The fewest and the most days a project can keep done items on its board.</summary>
    public const int FewestArchiveDays = 1;

    public const int MostArchiveDays = 365;

    /// <summary>The four columns an item is stored in, left to right.</summary>
    public static readonly IReadOnlyList<string> Columns = [Backlog, Todo, Doing, Done];

    /// <summary>The columns a board shows: the four, then <see cref="Dropped"/>.</summary>
    public static readonly IReadOnlyList<string> BoardColumns = [.. Columns, Dropped];

    /// <summary>The priorities, most important first.</summary>
    public static readonly IReadOnlyList<string> Priorities = [Urgent, High, Normal, Low];

    /// <summary>Who can make an item (supabase/migrations/0015_task_made_by.sql).</summary>
    public static readonly IReadOnlyList<string> Makers = [Owner, Claude];

    /// <summary>What the board's who-made-it switch can show: everything, or one maker's items.</summary>
    public static readonly IReadOnlyList<string> MakerFilters = [Everyone, Owner, Claude];

    /// <summary>The column a new item of this type lands in: an idea in the backlog, anything else in to do.</summary>
    public static string ColumnFor(string itemType) => itemType == Idea ? Backlog : Todo;

    /// <summary>How important a priority is; an unknown one counts as normal.</summary>
    public static int Rank(string priority) => priority switch
    {
        Urgent => 3,
        High => 2,
        Low => 0,
        _ => 1,
    };

    /// <summary>
    /// What moving an item to a board column does to a task in this state: dropped drops it, done
    /// finishes it, and any other column reopens a done or dropped one.
    /// </summary>
    public static TaskState Moved(string column, TaskState state) => column switch
    {
        Dropped => TaskState.Dropped,
        Done => TaskState.Done,
        _ when state is TaskState.Done or TaskState.Dropped => TaskState.Open,
        _ => state,
    };

    /// <summary>What finishing or reopening a task does to the column it sits in.</summary>
    public static string FinishedIn(TaskState state, string column) => state switch
    {
        TaskState.Done => Done,
        TaskState.Open when column == Done => Todo,
        _ => column,
    };

    /// <summary>One column's items in the order the board shows them.</summary>
    public static IReadOnlyList<TaskItem> Order(IEnumerable<TaskItem> items) =>
    [
        .. items
            .OrderByDescending(item => Rank(item.Priority))
            .ThenBy(item => item.Position)
            .ThenBy(item => item.CreatedAt, StringComparer.Ordinal)
            .ThenBy(item => item.Id, StringComparer.Ordinal),
    ];

    /// <summary>
    /// Whether the switch, set to <paramref name="filter"/>, shows an item made by
    /// <paramref name="madeBy"/>. An item that doesn't say is the owner's, and a filter nobody knows
    /// shows everything.
    /// </summary>
    public static bool Shows(string filter, string? madeBy) => filter switch
    {
        Owner or Claude => (madeBy ?? Owner) == filter,
        _ => true,
    };

    /// <summary>
    /// Whether an item is on its board (contracts/vectors/projects.json 'archive'): anything not done is;
    /// a done item is until it is archived by hand or <paramref name="archiveAfterDays"/> days after the
    /// planning day it was finished, and null days keep it until it is archived by hand.
    /// </summary>
    public static bool OnBoard(TaskState state, DateOnly? completedOn, int? archiveAfterDays, bool archivedByHand, DateOnly today)
    {
        if (state != TaskState.Done)
        {
            return true;
        }

        if (archivedByHand)
        {
            return false;
        }

        if (archiveAfterDays is not { } days || completedOn is not { } finished)
        {
            return true;
        }

        return today < finished.AddDays(days);
    }

    /// <summary>
    /// The board columns of a project with their items in order; a column with nothing in it stays. A
    /// dropped item is in <see cref="Dropped"/>, never in the column it is stored in.
    /// </summary>
    public static IReadOnlyList<ProjectColumn> Board(IReadOnlyList<TaskItem> items) =>
    [
        .. BoardColumns.Select(column =>
            new ProjectColumn(column, Order(items.Where(item => !item.Deleted && ShownIn(item.State, item.BoardColumn) == column)))),
    ];

    /// <summary>The board column an item shows in: <see cref="Dropped"/> for a dropped task, else the column it is stored in.</summary>
    public static string? ShownIn(TaskState state, string? column) => state == TaskState.Dropped ? Dropped : column;

    /// <summary>
    /// Whether <paramref name="key"/> can be a project's key once it is upper-cased: 2 to 6 capital
    /// letters or digits, starting with a letter (contracts/vectors/projects.json 'itemKeys').
    /// </summary>
    public static bool IsItemKey(string? key) =>
        key is { Length: >= ShortestItemKey and <= LongestItemKey }
        && char.IsAsciiLetter(key[0])
        && key.All(char.IsAsciiLetterOrDigit);

    /// <summary>
    /// A key for a project called <paramref name="name"/>: the capitals of one word (GoalMaker gives
    /// GM), the first letters of several (Jsi na tahu gives JNT), or the first three letters of one plain
    /// word (Thesis gives THE), at most four characters, with 2, 3 ... added while it is in
    /// <paramref name="taken"/> (any case). Null when the name has too little to make one from.
    /// </summary>
    public static string? SuggestItemKey(string name, IEnumerable<string> taken)
    {
        var words = Words(name).Where(word => !char.IsAsciiDigit(word[0])).ToList();
        var made = words.Count switch
        {
            0 => string.Empty,
            1 when words[0].Count(char.IsAsciiLetterUpper) >= 2 => string.Concat(words[0].Where(char.IsAsciiLetterUpper)),
            1 => words[0][..Math.Min(3, words[0].Length)],
            _ => string.Concat(words.Select(word => word[0])),
        };
        var key = made[..Math.Min(LongestSuggestedKey, made.Length)].ToUpperInvariant();
        if (key.Length < ShortestItemKey)
        {
            return null;
        }

        var used = taken.Select(other => other.ToUpperInvariant()).ToHashSet(StringComparer.Ordinal);
        var candidate = key;
        for (var number = 2; used.Contains(candidate); number++)
        {
            candidate = key + number.ToString(CultureInfo.InvariantCulture);
        }

        return IsItemKey(candidate) ? candidate : null;
    }

    /// <summary>An item's id as it reads: KEY-number, or #number in a project without a key.</summary>
    public static string FormatItemId(string? key, int number) =>
        key is { Length: > 0 }
            ? $"{key}-{number.ToString(CultureInfo.InvariantCulture)}"
            : "#" + number.ToString(CultureInfo.InvariantCulture);

    /// <summary>The id an item shows, or null while it has no number yet (the server gives it) or no project.</summary>
    public static string? ItemIdOf(TaskItem item, ProjectItem? project) =>
        item.ItemNumber is { } number && item.ProjectId is not null && project is not null ? FormatItemId(project.ItemKey, number) : null;

    /// <summary>
    /// Reads an id back, in any case and with spaces around it: KEY-number or #number, the number 1 or
    /// more. Null for anything else.
    /// </summary>
    public static ItemId? ParseItemId(string? text)
    {
        var trimmed = text?.Trim() ?? string.Empty;
        string? key;
        string digits;
        if (trimmed.StartsWith('#'))
        {
            key = null;
            digits = trimmed[1..];
        }
        else if (trimmed.IndexOf('-', StringComparison.Ordinal) is var dash and > 0)
        {
            key = trimmed[..dash].ToUpperInvariant();
            digits = trimmed[(dash + 1)..];
            if (!IsItemKey(key))
            {
                return null;
            }
        }
        else
        {
            return null;
        }

        return digits.Length > 0
            && digits.All(char.IsAsciiDigit)
            && int.TryParse(digits, NumberStyles.None, CultureInfo.InvariantCulture, out var number)
            && number >= 1
                ? new ItemId(key, number)
                : null;
    }

    /// <summary>
    /// Whether an item of a project keyed <paramref name="key"/>, numbered <paramref name="number"/>, is
    /// the one <paramref name="wanted"/> names. A #number names an item of a project without a key, or,
    /// with <paramref name="inProject"/> (a search inside one project's board), that project's item.
    /// </summary>
    public static bool IsItem(ItemId wanted, string? key, int? number, bool inProject = false) =>
        number == wanted.Number
        && (wanted.Key is null
            ? inProject || string.IsNullOrEmpty(key)
            : string.Equals(wanted.Key, key, StringComparison.OrdinalIgnoreCase));

    /// <summary>
    /// The items of <paramref name="tasks"/> that <paramref name="wanted"/> names, in their order: items of
    /// one of <paramref name="projects"/> (by id) with the number and the project's key, or #number for a
    /// project without one. A deleted item, or one whose project is gone, has no id.
    /// </summary>
    public static IEnumerable<TaskItem> Named(ItemId wanted, IEnumerable<TaskItem> tasks, IReadOnlyDictionary<string, ProjectItem> projects) =>
        tasks.Where(task => !task.Deleted
            && task.ProjectId is { } projectId
            && projects.GetValueOrDefault(projectId) is { Deleted: false } project
            && IsItem(wanted, project.ItemKey, task.ItemNumber));

    // The words of a name: its letters and digits, diacritics dropped, split at anything else.
    private static IEnumerable<string> Words(string name)
    {
        var word = new StringBuilder();
        foreach (var character in name.Normalize(NormalizationForm.FormD))
        {
            if (CharUnicodeInfo.GetUnicodeCategory(character) is UnicodeCategory.NonSpacingMark or UnicodeCategory.SpacingCombiningMark or UnicodeCategory.EnclosingMark)
            {
                continue;
            }

            if (char.IsAsciiLetterOrDigit(character))
            {
                word.Append(character);
            }
            else if (word.Length > 0)
            {
                yield return word.ToString();
                word.Clear();
            }
        }

        if (word.Length > 0)
        {
            yield return word.ToString();
        }
    }
}
