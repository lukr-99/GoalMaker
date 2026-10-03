namespace GoalMaker.App.ViewModels;

/// <summary>One project in the stats page's By project block: its name, what it finished, and its share of the busiest.</summary>
public sealed record StatsProjectViewModel(string Name, string Value, double Fraction);
