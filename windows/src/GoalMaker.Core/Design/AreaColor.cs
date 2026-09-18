namespace GoalMaker.Core.Design;

/// <summary>One of the 12 area colors, the same in every theme.</summary>
public sealed record AreaColor(string Id, uint Swatch, ChipColors Light, ChipColors Dark);
