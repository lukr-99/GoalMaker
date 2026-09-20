namespace GoalMaker.Core.Planning;

/// <summary>One column of a project's board with the items in it, in the order the board shows them.</summary>
public sealed record ProjectColumn(string Column, IReadOnlyList<TaskItem> Items);
