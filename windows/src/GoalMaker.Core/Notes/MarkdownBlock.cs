namespace GoalMaker.Core.Notes;

/// <summary>One line of a note: a list item when <see cref="Bullet"/>, otherwise plain text. An empty line has no spans.</summary>
public sealed record MarkdownBlock(bool Bullet, IReadOnlyList<MarkdownSpan> Spans);
