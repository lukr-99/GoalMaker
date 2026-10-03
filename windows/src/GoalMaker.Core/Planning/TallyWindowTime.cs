namespace GoalMaker.Core.Planning;

/// <summary>The minutes an app spent on one site or folder, as <see cref="TallyBreakdown.WindowLabel"/> names it.</summary>
public sealed record TallyWindowTime(string Label, int Minutes);
