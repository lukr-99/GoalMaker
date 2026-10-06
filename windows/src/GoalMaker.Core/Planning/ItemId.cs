namespace GoalMaker.Core.Planning;

/// <summary>
/// A project item's short id as someone typed it (docs/projects.md, "Item ids"): the project's key and
/// the item's number, GM-12, or only the number, #12, for a project without a key.
/// </summary>
public sealed record ItemId(string? Key, int Number);
