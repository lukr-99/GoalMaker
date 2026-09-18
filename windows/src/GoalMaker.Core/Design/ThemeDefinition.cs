namespace GoalMaker.Core.Design;

/// <summary>One of the switchable themes (ADR 0008), with its palettes for light, dark and pure black.</summary>
public sealed record ThemeDefinition(
    string Id,
    string Name,
    string Summary,
    ThemeTypography Typography,
    ThemeShapes Shapes,
    Palette Light,
    Palette Dark,
    Palette Black);
