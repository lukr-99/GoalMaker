namespace GoalMaker.Core.Planning;

/// <summary>The group a habit sits in on the Habits page (docs/habits.md), so a limit never reads as not done.</summary>
public enum HabitGroup
{
    /// <summary>Every day, or on chosen weekdays.</summary>
    Days,

    /// <summary>A number of times a week or a month.</summary>
    Weekly,

    /// <summary>A limit to stay under.</summary>
    Limits,
}
