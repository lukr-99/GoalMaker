namespace GoalMaker.Core.Planning;

/// <summary>
/// The board of a project (docs/projects.md, contracts/vectors/projects.json): where a new item lands,
/// how a column and the task's own state move together, the order items sit in, and which items the
/// who-made-it switch shows.
/// </summary>
public static class ProjectRules
{
    public const string Backlog = "backlog";
    public const string Todo = "todo";
    public const string Doing = "doing";
    public const string Done = "done";

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

    /// <summary>The four columns, left to right.</summary>
    public static readonly IReadOnlyList<string> Columns = [Backlog, Todo, Doing, Done];

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

    /// <summary>What moving an item to a column does to a task in this state.</summary>
    public static TaskState Moved(string column, TaskState state) => state switch
    {
        TaskState.Dropped => state,
        _ when column == Done => TaskState.Done,
        TaskState.Done => TaskState.Open,
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

    /// <summary>The four columns of a project with their items in order; a column with nothing in it stays.</summary>
    public static IReadOnlyList<ProjectColumn> Board(IReadOnlyList<TaskItem> items) =>
    [
        .. Columns.Select(column =>
            new ProjectColumn(column, Order(items.Where(item => !item.Deleted && item.BoardColumn == column)))),
    ];
}
