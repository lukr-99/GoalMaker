namespace GoalMaker.App.ViewModels;

/// <summary>One line of a picker (an area, a repeat); <see cref="Id"/> is null for "none". Equal by value, so a picker keeps its choice when the list is rebuilt.</summary>
public sealed record ChoiceViewModel(string? Id, string Label);
