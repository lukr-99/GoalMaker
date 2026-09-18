namespace GoalMaker.Core.Planning;

/// <summary>Every list the planner shows, computed together from the same tasks and planning day.</summary>
public sealed record PlanningLists(
    DateOnly Today,
    TodaySections TodaySections,
    IReadOnlyList<TaskItem> Tomorrow,
    IReadOnlyList<TaskItem> Inbox,
    DaySummary Summary);
