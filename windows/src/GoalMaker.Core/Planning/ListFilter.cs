namespace GoalMaker.Core.Planning;

/// <summary>
/// What the lists are narrowed to (spec, story 9; docs/lists.md): one area, one tag, both or neither.
/// It applies before the list rules, so every list and its summary show the same slice, and it stays
/// when the owner switches lists. Pinned by the 'filter' cases in contracts/vectors/lists.json.
/// </summary>
public sealed record ListFilter(string? AreaId = null, string? TagId = null)
{
    private static readonly IReadOnlySet<string> NoTags = new HashSet<string>();

    public static ListFilter None { get; } = new();

    public bool IsEmpty => AreaId is null && TagId is null;

    /// <summary>Whether <paramref name="task"/>, linked to the tags in <paramref name="tagIds"/>, stays in the lists.</summary>
    public bool Matches(TaskItem task, IReadOnlySet<string> tagIds) =>
        (AreaId is null || task.AreaId == AreaId) && (TagId is null || tagIds.Contains(TagId));

    /// <summary>The tasks that stay, in their order; <paramref name="tagLinks"/> maps a task id to the ids of its tags.</summary>
    public IReadOnlyList<TaskItem> Apply(IReadOnlyList<TaskItem> tasks, IReadOnlyDictionary<string, IReadOnlySet<string>> tagLinks) =>
        IsEmpty ? tasks : [.. tasks.Where(task => Matches(task, tagLinks.TryGetValue(task.Id, out var tags) ? tags : NoTags))];
}
