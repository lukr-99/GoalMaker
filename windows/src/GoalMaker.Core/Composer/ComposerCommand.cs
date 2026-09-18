namespace GoalMaker.Core.Composer;

/// <summary>A <c>/command</c> line: its lowercased name, the rest of the line, and whether the app knows it.</summary>
public sealed record ComposerCommand(string Name, string Argument, bool Known);
