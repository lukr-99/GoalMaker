namespace GoalMaker.Core.Planning;

/// <summary>Where a habit stands today (docs/habits.md), in the order the rules decide it.</summary>
public enum HabitStanding
{
    /// <summary>Archived, not started yet, or not due today.</summary>
    None,

    /// <summary>A pause covers today.</summary>
    Paused,

    /// <summary>The period holding today is skipped.</summary>
    Skipped,

    /// <summary>A limit: never done and never left, so it never reads as not done.</summary>
    Limit,

    /// <summary>Today's part is done: Hide done hides it.</summary>
    Done,

    /// <summary>Still to do today: Today's count of what is left counts it.</summary>
    Left,
}
