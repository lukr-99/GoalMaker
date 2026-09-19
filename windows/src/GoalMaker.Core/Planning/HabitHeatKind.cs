namespace GoalMaker.Core.Planning;

/// <summary>What a heatmap day shows (docs/habits.md).</summary>
public enum HabitHeatKind
{
    /// <summary>Before the habit starts, or a day it isn't due.</summary>
    None,

    Paused,

    Skipped,

    /// <summary>The day's value against its target.</summary>
    Share,
}
