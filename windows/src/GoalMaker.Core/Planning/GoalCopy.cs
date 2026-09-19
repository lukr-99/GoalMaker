namespace GoalMaker.Core.Planning;

/// <summary>A goal to create when last period's goals are copied into a new period (docs/goals.md).</summary>
public sealed record GoalCopy(string Title, string? Emoji, string Mode, double? Target, string? Unit, string? ParentId);
