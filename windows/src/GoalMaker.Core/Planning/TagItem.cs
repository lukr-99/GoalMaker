namespace GoalMaker.Core.Planning;

/// <summary>
/// A tag as the manager and the filters show it. It belongs to one area (<see cref="AreaId"/>) or one project
/// (<see cref="ProjectId"/>), or with neither it is for everything (docs/lists.md, "Tags").
/// </summary>
public sealed record TagItem(string Id, string Name, string? AreaId = null, string? ProjectId = null);
