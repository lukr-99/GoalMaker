namespace GoalMaker.Core.Planning;

/// <summary>
/// What a search on the Projects page looks for (docs/projects.md, "Finding items"): an item by its id,
/// GM-12 or #12, or by every word of the query in its title or notes, ignoring case and accents. The
/// words are folded once, when the query is read, and an item's text once per change of it
/// (<see cref="ProjectBoardIndex"/>), so a keystroke only compares strings.
/// </summary>
public sealed class ProjectSearch
{
    private readonly string[] words;

    private ProjectSearch(ItemId? id, string[] words)
    {
        Id = id;
        this.words = words;
    }

    /// <summary>The search that keeps everything: an empty query.</summary>
    public static ProjectSearch Everything { get; } = new(null, []);

    /// <summary>The item id the query names, or null when it is words.</summary>
    public ItemId? Id { get; }

    /// <summary>Whether the query keeps every item: nothing was typed.</summary>
    public bool IsEmpty => Id is null && words.Length == 0;

    public static ProjectSearch Of(string? query)
    {
        if (ProjectRules.ParseItemId(query) is { } id)
        {
            return new ProjectSearch(id, []);
        }

        var folded = (query ?? string.Empty).Split(' ', StringSplitOptions.RemoveEmptyEntries).Select(ArchiveRules.Fold).Where(word => word.Length > 0).ToArray();
        return folded.Length == 0 ? Everything : new ProjectSearch(null, folded);
    }

    /// <summary>
    /// Whether <paramref name="item"/>, whose folded title and notes are <paramref name="foldedText"/>,
    /// is found. <paramref name="projectKey"/> is its project's key; <paramref name="inProject"/> lets
    /// #12 name the item of the project on show even when that project has a key.
    /// </summary>
    public bool Finds(TaskItem item, string foldedText, string? projectKey, bool inProject)
    {
        if (Id is { } wanted)
        {
            return ProjectRules.IsItem(wanted, projectKey, item.ItemNumber, inProject);
        }

        foreach (var word in words)
        {
            if (!foldedText.Contains(word, StringComparison.Ordinal))
            {
                return false;
            }
        }

        return true;
    }
}
