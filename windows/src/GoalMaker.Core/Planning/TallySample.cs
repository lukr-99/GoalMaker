namespace GoalMaker.Core.Planning;

/// <summary>What was in front: an app (package or executable), and on Windows its window title.</summary>
public sealed record TallySample(string Platform, string App, string? Title = null);
