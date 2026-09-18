namespace GoalMaker.Core.Planning;

/// <summary>An area as lists and chips show it; <paramref name="ColorId"/> names a color in the area palette (themes.json).</summary>
public sealed record AreaItem(string Id, string Name, string ColorId, string? Emoji);
