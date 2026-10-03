namespace GoalMaker.App.ViewModels;

/// <summary>A board column's plus: what it says to a screen reader, and opening the new item window in that column.</summary>
public sealed record BoardColumnAdd(string Text, Action Open);
