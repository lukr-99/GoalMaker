using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One day of the week's dots on a habit card (contracts/vectors/habits.json, dots): what the day was,
/// its weekday's letter, and whether it is today.
/// </summary>
public sealed record HabitDotViewModel(HabitDot Kind, string Letter, bool IsToday)
{
    public bool IsMet => Kind == HabitDot.Met;

    public bool IsMissed => Kind == HabitDot.Missed;

    public bool IsOver => Kind == HabitDot.Over;

    public bool IsOpen => Kind == HabitDot.Open;

    public bool IsSkipped => Kind == HabitDot.Skipped;

    public bool IsPaused => Kind == HabitDot.Paused;
}
