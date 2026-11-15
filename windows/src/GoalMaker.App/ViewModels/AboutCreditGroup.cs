namespace GoalMaker.App.ViewModels;

/// <summary>A heading on the credits card and the credits under it.</summary>
public sealed record AboutCreditGroup(string Title, IReadOnlyList<AboutCredit> Credits);
