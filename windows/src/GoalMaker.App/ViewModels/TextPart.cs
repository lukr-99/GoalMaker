namespace GoalMaker.App.ViewModels;

/// <summary>A piece of a search result's title: a part the search matched lights up (docs/projects.md, "Searching items").</summary>
public sealed record TextPart(string Text, bool IsMatch);
