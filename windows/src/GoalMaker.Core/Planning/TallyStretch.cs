namespace GoalMaker.Core.Planning;

/// <summary>
/// A stretch one window was in front, in local time, sorted into a category with the rules as they are
/// now. It comes from this PC's own log and never syncs (ADR 0013).
/// </summary>
public sealed record TallyStretch(DateTime Start, DateTime End, string App, string? Title, string Category);
