namespace GoalMaker.Core.Planning;

/// <summary>One group of tags in Settings: the tags for everything (<see cref="Place"/> null), an area's or a project's.</summary>
public sealed record TagGroup(TagPlace? Place, IReadOnlyList<TagItem> Tags);
