namespace GoalMaker.Core.Design;

/// <summary>One of the area palette's colors, the same in every theme.</summary>
public sealed record AreaColor(string Id, uint Swatch, ChipColors Light, ChipColors Dark);
