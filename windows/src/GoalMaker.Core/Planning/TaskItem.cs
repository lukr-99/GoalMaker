namespace GoalMaker.Core.Planning;

/// <summary>A task as the lists show it. <see cref="SeriesId"/> groups a repeating task's occurrences (docs/repeating.md).</summary>
public sealed record TaskItem(
    string Id,
    string Title,
    TaskState State,
    bool TopPriority,
    string CreatedAt,
    DateOnly? PlannedDate = null,
    TimeOnly? PlannedTime = null,
    string? AreaId = null,
    string? Recurrence = null,
    bool Deleted = false,
    string? SeriesId = null);
