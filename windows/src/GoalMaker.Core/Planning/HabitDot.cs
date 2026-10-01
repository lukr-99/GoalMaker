namespace GoalMaker.Core.Planning;

/// <summary>One day of the week's dots on a habit card (docs/habits.md).</summary>
public enum HabitDot
{
    /// <summary>Before the habit starts, a day it isn't due, or a weekly habit's day without a check-in.</summary>
    None,

    Paused,

    Skipped,

    /// <summary>A limit's day that went over the number.</summary>
    Over,

    /// <summary>Today, still to come.</summary>
    Open,

    Met,

    Missed,
}
