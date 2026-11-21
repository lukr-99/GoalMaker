namespace GoalMaker.Core.Planning;

/// <summary>
/// Tags that belong to an area or a project (docs/lists.md, "Tags"): which ones a filter offers, and how Settings
/// groups them. A tag whose area or project is gone counts as for everything. Pinned by contracts/vectors/tags.json.
/// </summary>
public static class TagRules
{
    /// <summary>
    /// The tags a tag picker offers, in their order: every tag with nothing chosen; with <paramref name="areaId"/>, the
    /// tags for everything and that area's; on a project's board (<paramref name="projectId"/>), the tags for
    /// everything, that project's and its area's. <paramref name="projectAreas"/> maps each live project to its area.
    /// </summary>
    public static IReadOnlyList<TagItem> Offered(
        IReadOnlyList<TagItem> tags,
        IReadOnlySet<string> areaIds,
        IReadOnlyDictionary<string, string?> projectAreas,
        string? areaId = null,
        string? projectId = null)
    {
        if (areaId is null && projectId is null) return tags;
        var area = areaId ?? (projectId is not null ? projectAreas.GetValueOrDefault(projectId) : null);
        var projectIds = projectAreas.Keys.ToHashSet(StringComparer.Ordinal);
        return [.. tags.Where(tag => PlaceOf(tag, areaIds, projectIds) switch
        {
            null => true,
            { AreaId: { } owner } => owner == area,
            { ProjectId: { } owner } => owner == projectId,
            _ => true,
        })];
    }

    /// <summary>Settings' groups: the tags for everything, then each area's in order, then each project's; empty ones left out.</summary>
    public static IReadOnlyList<TagGroup> Groups(IReadOnlyList<TagItem> tags, IReadOnlyList<string> areaIds, IReadOnlyList<string> projectIds)
    {
        var areas = areaIds.ToHashSet(StringComparer.Ordinal);
        var projects = projectIds.ToHashSet(StringComparer.Ordinal);
        var places = new List<TagPlace?> { null };
        places.AddRange(areaIds.Select(TagPlace.Area));
        places.AddRange(projectIds.Select(TagPlace.Project));
        return [.. places
            .Select(place => new TagGroup(place, [.. tags.Where(tag => PlaceOf(tag, areas, projects) == place)]))
            .Where(group => group.Tags.Count > 0)];
    }

    /// <summary>Where a tag belongs, or null when it is for everything (or its area or project is gone).</summary>
    public static TagPlace? PlaceOf(TagItem tag, IReadOnlySet<string> areaIds, IReadOnlySet<string> projectIds) =>
        tag.AreaId is { } area && areaIds.Contains(area) ? TagPlace.Area(area)
        : tag.ProjectId is { } project && projectIds.Contains(project) ? TagPlace.Project(project)
        : null;
}
