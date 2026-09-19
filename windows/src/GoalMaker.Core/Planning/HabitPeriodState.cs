namespace GoalMaker.Core.Planning;

/// <summary>Where one period of a habit stands (docs/habits.md), in the order the rules decide it.</summary>
public enum HabitPeriodState
{
    /// <summary>Before the habit starts, or a day it isn't due.</summary>
    None,

    /// <summary>Enough days in it were met.</summary>
    Met,

    /// <summary>A pause covers one of its days.</summary>
    Paused,

    /// <summary>A check-in in it says skipped.</summary>
    Skipped,

    /// <summary>It hasn't ended yet.</summary>
    Open,

    Missed,
}
