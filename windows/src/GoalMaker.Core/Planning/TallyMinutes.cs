namespace GoalMaker.Core.Planning;

/// <summary>The minutes that went to one category, project or device kind; <see cref="Key"/> is null for none.</summary>
public sealed record TallyMinutes(string? Key, int Minutes);
