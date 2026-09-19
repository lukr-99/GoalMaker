namespace GoalMaker.Core.Planning;

/// <summary>Where a goal stands: <paramref name="Value"/> of <paramref name="Target"/>, <paramref name="Fraction"/> (0 to 1) for its ring or bar, and whether it is <paramref name="Hit"/>.</summary>
public sealed record GoalProgress(double Value, double Target, double Fraction, bool Hit);
