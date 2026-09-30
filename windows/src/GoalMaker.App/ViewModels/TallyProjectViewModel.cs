namespace GoalMaker.App.ViewModels;

/// <summary>A project's time on the Tally page, and its share of the project with the most.</summary>
public sealed record TallyProjectViewModel(string Name, string Value, double Fraction);
