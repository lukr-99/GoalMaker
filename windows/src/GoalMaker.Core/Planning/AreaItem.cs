namespace GoalMaker.Core.Planning;

/// <summary>
/// An area as lists and chips show it; <paramref name="ColorId"/> names a color in the area palette
/// (themes.json). An <paramref name="Archived"/> area keeps its tasks and chips but leaves the pickers
/// and filters (docs/lists.md).
/// </summary>
public sealed record AreaItem(string Id, string Name, string ColorId, string? Emoji, bool Archived = false);
