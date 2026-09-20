namespace GoalMaker.Core.Planning;

/// <summary>Goal periods, the cascade and progress (docs/goals.md, contracts/vectors/goals.json).</summary>
public static class GoalRules
{
    public const string ModeDone = "done";
    public const string ModeTasks = "tasks";
    public const string ModeNumber = "number";
    public const string Open = "open";
    public const string Done = "done";
    public const string Dropped = "dropped";

    /// <summary>How the server and the vectors name a horizon.</summary>
    public static string Id(GoalHorizon horizon) => horizon.ToString().ToLowerInvariant();

    /// <summary>The horizon a name stands for, or null.</summary>
    public static GoalHorizon? HorizonOf(string? id) => id switch
    {
        "year" => GoalHorizon.Year,
        "month" => GoalHorizon.Month,
        "week" => GoalHorizon.Week,
        "day" => GoalHorizon.Day,
        _ => null,
    };

    /// <summary>The first day of the <paramref name="horizon"/> period <paramref name="day"/> falls in (weeks start on Monday).</summary>
    public static DateOnly PeriodStart(GoalHorizon horizon, DateOnly day) => horizon switch
    {
        GoalHorizon.Year => new DateOnly(day.Year, 1, 1),
        GoalHorizon.Month => new DateOnly(day.Year, day.Month, 1),
        GoalHorizon.Week => day.AddDays(-(((int)day.DayOfWeek + 6) % 7)),
        _ => day,
    };

    /// <summary>The last day of the <paramref name="horizon"/> period starting on <paramref name="start"/>.</summary>
    public static DateOnly PeriodEnd(GoalHorizon horizon, DateOnly start) => horizon switch
    {
        GoalHorizon.Year => new DateOnly(start.Year, 12, 31),
        GoalHorizon.Month => new DateOnly(start.Year, start.Month, DateTime.DaysInMonth(start.Year, start.Month)),
        GoalHorizon.Week => start.AddDays(6),
        _ => start,
    };

    /// <summary>Whether a goal of <paramref name="parent"/> can be the parent of a goal of <paramref name="child"/>: a longer horizon whose period overlaps.</summary>
    public static bool CanServe(GoalHorizon child, DateOnly childStart, GoalHorizon parent, DateOnly parentStart) =>
        parent > child && parentStart <= PeriodEnd(child, childStart) && childStart <= PeriodEnd(parent, parentStart);

    /// <summary>
    /// Where a goal of <paramref name="mode"/> and <paramref name="status"/> stands, from the tasks that
    /// serve it and the entries logged on it; <paramref name="target"/> is a numeric goal's.
    /// </summary>
    public static GoalProgress Progress(string mode, string status, double? target, IEnumerable<TaskItem> tasks, IEnumerable<GoalEntryItem> entries)
    {
        double value;
        double goal;
        switch (mode)
        {
            case ModeTasks:
                var counted = tasks.Where(task => !task.Deleted && task.State != TaskState.Dropped).ToList();
                value = counted.Count(task => task.State == TaskState.Done);
                goal = counted.Count;
                break;
            case ModeNumber:
                value = entries.Where(entry => !entry.Deleted).Sum(entry => entry.Amount);
                goal = target ?? 0;
                break;
            default:
                value = status == Done ? 1 : 0;
                goal = 1;
                break;
        }

        var fraction = status == Done ? 1 : goal <= 0 ? 0 : Math.Clamp(value / goal, 0, 1);
        var hit = status == Done || (status != Dropped && goal > 0 && value >= goal);
        return new GoalProgress(value, goal, fraction, hit);
    }

    /// <summary>
    /// Where <paramref name="goal"/> stands, from the tasks that serve it, the entries logged on it and
    /// the check-ins of the habits that feed a numeric goal (docs/habits.md).
    /// </summary>
    public static GoalProgress ProgressOf(
        GoalItem goal,
        IEnumerable<TaskItem> tasks,
        IEnumerable<GoalEntryItem> entries,
        IReadOnlyList<HabitItem> habits,
        IReadOnlyList<HabitCheckin> checkins)
    {
        IEnumerable<GoalEntryItem> amounts = goal.Mode == ModeNumber && habits.Count > 0
            ? HabitRules.GoalAmounts(goal, habits, checkins).Select(amount => new GoalEntryItem(string.Empty, goal.Id, goal.PeriodStart, amount))
            : [];
        return Progress(goal.Mode, goal.Status, goal.Target, tasks, [.. entries, .. amounts]);
    }

    /// <summary>
    /// Last period's goals as copies for a new <paramref name="horizon"/> period starting on
    /// <paramref name="start"/>: all but the dropped, each keeping its parent only when that goal still
    /// overlaps the period.
    /// </summary>
    public static IReadOnlyList<GoalCopy> Copies(
        IEnumerable<GoalItem> goals,
        IReadOnlyDictionary<string, GoalItem> parents,
        GoalHorizon horizon,
        DateOnly start) =>
        [.. goals.Where(goal => !goal.Deleted && goal.Status != Dropped).Select(goal =>
        {
            var parent = goal.ParentId is { } id && parents.TryGetValue(id, out var found) && CanServe(horizon, start, found.Horizon, found.PeriodStart)
                ? found.Id
                : null;
            return new GoalCopy(goal.Title, goal.Emoji, goal.Mode, goal.Target, goal.Unit, parent);
        })];
}
