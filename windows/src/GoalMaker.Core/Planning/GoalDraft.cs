namespace GoalMaker.Core.Planning;

/// <summary>What a new or edited goal says (docs/goals.md); <see cref="PeriodStart"/> is moved to its period's first day.</summary>
public sealed record GoalDraft(
    string Title,
    GoalHorizon Horizon,
    DateOnly PeriodStart,
    string Mode = GoalRules.ModeDone,
    string? Emoji = null,
    string? ParentId = null,
    double? Target = null,
    string? Unit = null);
