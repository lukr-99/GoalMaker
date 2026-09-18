namespace GoalMaker.Core.Planning;

/// <summary>A task as the lists show it. M2-06 adds deadlines, steps and tags to what lists show.</summary>
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
    bool Deleted = false);
