namespace GoalMaker.App.ViewModels;

/// <summary>
/// One bar of a stats chart (docs/stats.md): what it stands for, the number on it, and how full it is
/// against the tallest bar of its chart. A week of the tasks chart also says how much of it was project
/// work (<see cref="ProjectFraction"/>, against the same tallest bar) and names itself in <see cref="Tip"/>.
/// </summary>
public sealed record StatsBarViewModel(string Label, string Value, double Fraction, bool Filled, double ProjectFraction = 0, string Tip = "")
{
    /// <summary>Whether part of the bar is project work, drawn in the full accent under the rest.</summary>
    public bool HasProject => ProjectFraction > 0;
}
