namespace GoalMaker.Core.Design;

/// <summary>Spacing for this device in device-independent pixels (Windows is compact; spacing never comes from the theme).</summary>
public sealed record Density(int RowMinHeight, int RowGap, int PagePadding, int CardPadding);
