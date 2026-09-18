namespace GoalMaker.Core.Planning;

/// <summary>Today's open tasks by section (docs/lists.md), each in its order.</summary>
public sealed record TodaySections(
    IReadOnlyList<TaskItem> Priorities,
    IReadOnlyList<TaskItem> Scheduled,
    IReadOnlyList<TaskItem> More,
    IReadOnlyList<TaskItem> Overdue)
{
    public bool IsEmpty => Priorities.Count == 0 && Scheduled.Count == 0 && More.Count == 0 && Overdue.Count == 0;
}
