namespace GoalMaker.Core.Planning;

/// <summary>A device's minutes on one planning day in one category and project.</summary>
public sealed record TallyTotal(DateOnly Day, string Category, string? Project, int Minutes);
