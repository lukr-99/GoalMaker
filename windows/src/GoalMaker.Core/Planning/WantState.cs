namespace GoalMaker.Core.Planning;

/// <summary>Where a want stands on a planning day (docs/wants.md).</summary>
public enum WantState
{
    /// <summary>Before the day it cools.</summary>
    Cooling,

    /// <summary>On or after the day it cools, and not decided yet.</summary>
    Ready,

    /// <summary>Bought or dropped, whatever the day.</summary>
    Decided,
}
