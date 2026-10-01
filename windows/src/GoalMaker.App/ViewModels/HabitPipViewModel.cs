namespace GoalMaker.App.ViewModels;

/// <summary>One pip under a habit's name: a glass of a small count, or a day a weekly habit needs; past a limit's line it is over.</summary>
public sealed record HabitPipViewModel(bool IsOn, bool IsOver);
