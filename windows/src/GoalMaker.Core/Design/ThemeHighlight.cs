namespace GoalMaker.Core.Design;

/// <summary>One theme's Settings highlight in light and in dark; pure black uses the dark one.</summary>
public sealed record ThemeHighlight(HighlightTokens Light, HighlightTokens Dark);
