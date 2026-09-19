namespace GoalMaker.Core.Planning;

/// <summary>
/// A goal as the screens and rules see it (docs/goals.md). <see cref="Mode"/> is done, tasks or number;
/// <see cref="Status"/> is open, done or dropped; <see cref="Target"/> and <see cref="Unit"/> belong to a numeric goal.
/// </summary>
public sealed record GoalItem(string Id, string Title, GoalHorizon Horizon, DateOnly PeriodStart)
{
    public string Mode { get; init; } = GoalRules.ModeDone;

    public string Status { get; init; } = GoalRules.Open;

    public string? Emoji { get; init; }

    public string? ParentId { get; init; }

    public double? Target { get; init; }

    public string? Unit { get; init; }

    public string? CompletedAt { get; init; }

    public double Position { get; init; }

    public bool Deleted { get; init; }
}
