namespace GoalMaker.Core.Notes;

/// <summary>
/// One line of a note: a list item when <see cref="Bullet"/>, a heading of level 1 to 3 when
/// <see cref="Heading"/> is above 0, otherwise plain text. An empty line has no spans.
/// </summary>
public sealed record MarkdownBlock(bool Bullet, IReadOnlyList<MarkdownSpan> Spans, int Heading = 0);
