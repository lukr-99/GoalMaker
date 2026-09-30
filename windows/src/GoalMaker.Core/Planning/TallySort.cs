namespace GoalMaker.Core.Planning;

/// <summary>Where a moment of foreground time goes: a category, and on the PC maybe a project.</summary>
public sealed record TallySort(string Category, string? Project);
