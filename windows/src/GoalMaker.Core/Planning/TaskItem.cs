namespace GoalMaker.Core.Planning;

/// <summary>A task as the lists show it. M2 adds the planning fields (days, deadlines, areas, tags).</summary>
public sealed record TaskItem(string Id, string Title, TaskState State, bool TopPriority, string CreatedAt);
