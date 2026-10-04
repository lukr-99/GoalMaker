namespace GoalMaker.Core.Planning;

/// <summary>What the owner types for a life goal (docs/life-goals.md).</summary>
public sealed record LifeGoalDraft(
    string Title,
    string Why,
    DateOnly? By = null,
    string? AreaId = null);
