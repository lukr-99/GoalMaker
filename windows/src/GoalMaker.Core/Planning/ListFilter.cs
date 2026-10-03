namespace GoalMaker.Core.Planning;

/// <summary>
/// What a place is narrowed to (spec, story 9; docs/lists.md): one area, one tag, both or neither.
/// It applies before the list rules, so every list and its summary show the same slice, and it stays
/// when the owner switches lists. The Projects board, the calendar and the archive narrow by the same
/// rule. A task without an area of its own counts as being in its project's area. Pinned by the
/// 'filter' cases in contracts/vectors/lists.json.
/// </summary>
public sealed record ListFilter(string? AreaId = null, string? TagId = null)
{
    private static readonly IReadOnlySet<string> NoTags = new HashSet<string>();
    private static readonly IReadOnlyDictionary<string, string?> NoProjects = new Dictionary<string, string?>();

    public static ListFilter None { get; } = new();

    public bool IsEmpty => AreaId is null && TagId is null;

    /// <summary>
    /// Whether <paramref name="task"/>, linked to the tags in <paramref name="tagIds"/>, stays;
    /// <paramref name="projectAreaId"/> is its project's area.
    /// </summary>
    public bool Matches(TaskItem task, IReadOnlySet<string> tagIds, string? projectAreaId = null) =>
        (AreaId is null || (task.AreaId ?? projectAreaId) == AreaId) && (TagId is null || tagIds.Contains(TagId));

    /// <summary>
    /// The tasks that stay, in their order; <paramref name="tagLinks"/> maps a task id to the ids of its
    /// tags, and <paramref name="projectAreas"/> a project id to its area.
    /// </summary>
    public IReadOnlyList<TaskItem> Apply(
        IReadOnlyList<TaskItem> tasks,
        IReadOnlyDictionary<string, IReadOnlySet<string>> tagLinks,
        IReadOnlyDictionary<string, string?>? projectAreas = null) =>
        IsEmpty ? tasks : [.. tasks.Where(task => Keeps(task, tagLinks, projectAreas))];

    /// <summary><see cref="Matches"/> with the task's tags and its project's area looked up.</summary>
    public bool Keeps(
        TaskItem task,
        IReadOnlyDictionary<string, IReadOnlySet<string>> tagLinks,
        IReadOnlyDictionary<string, string?>? projectAreas = null) =>
        Matches(
            task,
            tagLinks.TryGetValue(task.Id, out var tags) ? tags : NoTags,
            task.ProjectId is { } project ? (projectAreas ?? NoProjects).GetValueOrDefault(project) : null);

    /// <summary>
    /// Whether a project in <paramref name="projectAreaId"/> stays in the project list: always without
    /// a filter; else when its own area is the one chosen and no tag is, or when the filter keeps one of
    /// its <paramref name="items"/>.
    /// </summary>
    public bool KeepsProject(string? projectAreaId, IEnumerable<TaskItem> items, IReadOnlyDictionary<string, IReadOnlySet<string>> tagLinks) =>
        IsEmpty
        || (TagId is null && projectAreaId == AreaId)
        || items.Any(item => Matches(item, tagLinks.TryGetValue(item.Id, out var tags) ? tags : NoTags, projectAreaId));
}
