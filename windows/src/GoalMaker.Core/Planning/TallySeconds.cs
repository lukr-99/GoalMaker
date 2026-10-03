namespace GoalMaker.Core.Planning;

/// <summary>One category's seconds in an hour of the day, as <see cref="TallyBreakdown.Hours"/> adds them up.</summary>
public sealed record TallySeconds(string Category, int Seconds);
