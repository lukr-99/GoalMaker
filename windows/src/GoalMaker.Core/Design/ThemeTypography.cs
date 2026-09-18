namespace GoalMaker.Core.Design;

/// <summary>A theme's three text styles (docs/design/spec.md, "Type").</summary>
public sealed record ThemeTypography(TypeStyle Heading, BodyStyle Body, TypeStyle Number);
