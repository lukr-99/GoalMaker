namespace GoalMaker.Core.Planning;

/// <summary>The period a goal belongs to; the value is its rank, longest highest (docs/goals.md).</summary>
public enum GoalHorizon
{
    Day = 0,
    Week = 1,
    Month = 2,
    Year = 3,
}
