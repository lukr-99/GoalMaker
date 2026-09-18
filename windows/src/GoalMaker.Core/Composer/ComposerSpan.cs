namespace GoalMaker.Core.Composer;

/// <summary>A recognized part of the line: <paramref name="Start"/> to <paramref name="End"/> (exclusive) in UTF-16 code units.</summary>
public sealed record ComposerSpan(SpanKind Kind, int Start, int End);
