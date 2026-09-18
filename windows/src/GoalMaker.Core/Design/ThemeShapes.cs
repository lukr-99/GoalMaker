namespace GoalMaker.Core.Design;

/// <summary>Corner radii in device-independent pixels; <see cref="FullyRound"/> or more means a pill or a circle.</summary>
public sealed record ThemeShapes(int Card, int Row, int Checkbox, int Button)
{
    public const int FullyRound = 999;
}
