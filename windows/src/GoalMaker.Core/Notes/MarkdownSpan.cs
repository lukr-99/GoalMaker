namespace GoalMaker.Core.Notes;

/// <summary>A run of text in a note with one style; <see cref="Link"/> is the address when the run is a link.</summary>
public sealed record MarkdownSpan(string Text, bool Bold = false, bool Italic = false, string? Link = null);
