namespace GoalMaker.Core.Planning;

/// <summary>One area's tasks on the All tasks place; a null <see cref="AreaId"/> is the group without an area.</summary>
public sealed record TaskGroup(string? AreaId, IReadOnlyList<TaskItem> Tasks);
