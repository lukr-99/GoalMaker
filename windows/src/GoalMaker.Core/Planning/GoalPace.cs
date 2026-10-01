namespace GoalMaker.Core.Planning;

/// <summary>
/// Where a goal stands against the share of its period gone by (docs/goals.md), in the order the Goals
/// page sorts by: the goals that need you first.
/// </summary>
public enum GoalPace
{
    Behind = 0,
    OnTrack = 1,
    Hit = 2,
    Dropped = 3,
}
