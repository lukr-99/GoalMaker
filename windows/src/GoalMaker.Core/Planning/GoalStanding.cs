namespace GoalMaker.Core.Planning;

/// <summary>A goal's <paramref name="Pace"/>, and for one counted by tasks or a number that is behind, the amount it is <paramref name="Behind"/> by.</summary>
public sealed record GoalStanding(GoalPace Pace, double? Behind = null);
