namespace GoalMaker.App.ViewModels;

/// <summary>
/// One bar of a stats chart (docs/stats.md): what it stands for, the number on it, and how full it is
/// against the tallest bar of its chart.
/// </summary>
public sealed record StatsBarViewModel(string Label, string Value, double Fraction, bool Filled);
