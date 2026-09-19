namespace GoalMaker.Core.Planning;

/// <summary>The Plan tomorrow ritual's rules (docs/plan-tomorrow.md, contracts/vectors/plan.json).</summary>
public static class PlanRules
{
    /// <summary>The most top priorities the ritual lets tomorrow have.</summary>
    public const int MaxPriorities = 3;

    /// <summary>The composer command that opens the ritual (docs/composer.md).</summary>
    public const string Command = "plan";

    /// <summary>Step 1: open tasks planned for <paramref name="today"/> or earlier, oldest day first.</summary>
    public static IReadOnlyList<TaskItem> Review(IEnumerable<TaskItem> tasks, DateOnly today) =>
        [.. tasks.Where(task => !task.Deleted && task.State == TaskState.Open && task.PlannedDate <= today)
            .OrderBy(task => task.PlannedDate)
            .ThenBy(task => task.PlannedTime is null)
            .ThenBy(task => task.PlannedTime)
            .ThenBy(task => task.CreatedAt, StringComparer.Ordinal)
            .ThenBy(task => task.Id, StringComparer.Ordinal)];

    /// <summary>What the ritual shows for <paramref name="task"/>, read from its state so changes from elsewhere show up.</summary>
    public static PlanDecision Decision(TaskItem task, DateOnly today) => task switch
    {
        { State: TaskState.Done } => PlanDecision.Done,
        { State: TaskState.Dropped } => PlanDecision.Dropped,
        { PlannedDate: null } => PlanDecision.Unplanned,
        { PlannedDate: { } day } when day <= today => PlanDecision.Undecided,
        { PlannedDate: { } day } when day == today.AddDays(1) => PlanDecision.Tomorrow,
        _ => PlanDecision.Later,
    };

    /// <summary>Step 2: tomorrow's open tasks by time, so flagging a priority doesn't move a row.</summary>
    public static IReadOnlyList<TaskItem> Tomorrow(IEnumerable<TaskItem> tasks, DateOnly today)
    {
        var tomorrow = today.AddDays(1);
        return [.. tasks.Where(task => !task.Deleted && task.State == TaskState.Open && task.PlannedDate == tomorrow)
            .OrderBy(task => task.PlannedTime is null)
            .ThenBy(task => task.PlannedTime)
            .ThenBy(task => task.CreatedAt, StringComparer.Ordinal)
            .ThenBy(task => task.Id, StringComparer.Ordinal)];
    }

    /// <summary>How many of tomorrow's open tasks are top priorities.</summary>
    public static int Priorities(IEnumerable<TaskItem> tasks, DateOnly today)
    {
        var tomorrow = today.AddDays(1);
        return tasks.Count(task => !task.Deleted && task.State == TaskState.Open && task.TopPriority && task.PlannedDate == tomorrow);
    }

    /// <summary>
    /// How often a task was moved once it goes from <paramref name="before"/> to <paramref name="after"/>
    /// (tasks.moved_count, docs/reviews.md): moving a planned task to another day counts, planning one
    /// that had no day doesn't, and neither does taking its day away.
    /// </summary>
    public static int Moves(DateOnly? before, DateOnly? after, int count) =>
        before is { } from && after is { } to && from != to ? count + 1 : count;
}
