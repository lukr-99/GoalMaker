namespace GoalMaker.Core.Problems;

/// <summary>
/// Something that went wrong while nobody was watching (docs/problems.md): which
/// <paramref name="Kind"/> of thing it was, when, what to keep behind "what happened" for a bug
/// report, and whether the owner has seen it yet.
/// </summary>
public sealed record Problem(string Kind, DateTimeOffset At, string? Detail, bool Unread = true);
