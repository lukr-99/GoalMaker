namespace GoalMaker.App.ViewModels;

/// <summary>One habit on the stats page: how much of the window it held, and the run it is on now.</summary>
public sealed record StatsHabitViewModel(string Emoji, string Name, string Met, string Rate, double Fraction, string Streak);
