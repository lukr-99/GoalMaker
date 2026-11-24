namespace GoalMaker.Core.Planning;

/// <summary>
/// The Projects page's data, read once per change instead of once per keystroke (GM-121): the projects,
/// each one's items, how much its open columns hold, the tags of every item, and every item's title and
/// notes folded for search. A new index keeps the folded text of the items whose title and notes did
/// not change, so a change from sync folds only what changed.
/// </summary>
public sealed class ProjectBoardIndex
{
    private static readonly IReadOnlySet<string> NoTags = new HashSet<string>();
    private readonly Dictionary<string, ProjectItem> byId;
    private readonly Dictionary<string, List<TaskItem>> items;
    private readonly Dictionary<string, ProjectLoad> loads;
    private readonly Dictionary<string, Folded> folded;

    private ProjectBoardIndex(
        IReadOnlyList<ProjectItem> projects,
        Dictionary<string, List<TaskItem>> items,
        IReadOnlyDictionary<string, IReadOnlySet<string>> tagLinks,
        Dictionary<string, Folded> folded)
    {
        Projects = projects;
        byId = projects.ToDictionary(project => project.Id, StringComparer.Ordinal);
        this.items = items;
        TagLinks = tagLinks;
        this.folded = folded;
        loads = new Dictionary<string, ProjectLoad>(StringComparer.Ordinal);
        foreach (var (projectId, own) in items)
        {
            int backlog = 0, todo = 0, doing = 0;
            foreach (var item in own)
            {
                switch (ProjectRules.ShownIn(item.State, item.BoardColumn))
                {
                    case ProjectRules.Backlog: backlog++; break;
                    case ProjectRules.Todo: todo++; break;
                    case ProjectRules.Doing: doing++; break;
                }
            }

            loads[projectId] = new ProjectLoad(backlog, todo, doing);
        }
    }

    public static ProjectBoardIndex Empty { get; } = Build([], [], new Dictionary<string, IReadOnlySet<string>>());

    /// <summary>The projects, in the order the project list shows them.</summary>
    public IReadOnlyList<ProjectItem> Projects { get; }

    /// <summary>Every item's tag ids by the item's id.</summary>
    public IReadOnlyDictionary<string, IReadOnlySet<string>> TagLinks { get; }

    /// <summary>
    /// Reads <paramref name="tasks"/> into an index; <paramref name="previous"/>, the index before the
    /// change, lends the folded text of every item whose title and notes are the same.
    /// </summary>
    public static ProjectBoardIndex Build(
        IReadOnlyList<ProjectItem> projects,
        IReadOnlyList<TaskItem> tasks,
        IReadOnlyDictionary<string, IReadOnlySet<string>> tagLinks,
        ProjectBoardIndex? previous = null)
    {
        var items = new Dictionary<string, List<TaskItem>>(StringComparer.Ordinal);
        var folded = new Dictionary<string, Folded>(StringComparer.Ordinal);
        foreach (var task in tasks)
        {
            if (task.ProjectId is not { } projectId || task.Deleted)
            {
                continue;
            }

            if (!items.TryGetValue(projectId, out var own))
            {
                own = [];
                items[projectId] = own;
            }

            own.Add(task);
            folded[task.Id] = previous?.folded.GetValueOrDefault(task.Id) is { } kept && kept.Title == task.Title && kept.Notes == task.Notes
                ? kept
                : new Folded(task.Title, task.Notes, ArchiveRules.Fold(task.Title) + "\n" + ArchiveRules.Fold(task.Notes));
        }

        return new ProjectBoardIndex(projects, items, tagLinks, folded);
    }

    public ProjectItem? Project(string? id) => id is not null ? byId.GetValueOrDefault(id) : null;

    /// <summary>The items of a project, whatever their state, in the order they were read.</summary>
    public IReadOnlyList<TaskItem> ItemsOf(string projectId) => items.TryGetValue(projectId, out var own) ? own : [];

    /// <summary>How much the project's Backlog, To do and Doing hold.</summary>
    public ProjectLoad LoadOf(string projectId) => loads.GetValueOrDefault(projectId) ?? ProjectLoad.None;

    public IReadOnlySet<string> TagsOf(string taskId) => TagLinks.GetValueOrDefault(taskId) ?? NoTags;

    /// <summary>Whether <paramref name="search"/> finds <paramref name="item"/>; <paramref name="inProject"/> as in <see cref="ProjectSearch.Finds"/>.</summary>
    public bool Finds(ProjectSearch search, TaskItem item, bool inProject) =>
        search.IsEmpty
        || search.Finds(item, folded.GetValueOrDefault(item.Id)?.Text ?? string.Empty, Project(item.ProjectId)?.ItemKey, inProject);

    /// <summary>
    /// The items of every project the search finds, open ones first and newest first, at most
    /// <paramref name="limit"/>; <paramref name="total"/> is how many there were in all. An empty search finds nothing.
    /// </summary>
    public IReadOnlyList<TaskItem> Search(ProjectSearch search, int limit, out int total)
    {
        if (search.IsEmpty)
        {
            total = 0;
            return [];
        }

        var found = new List<TaskItem>();
        foreach (var project in Projects)
        {
            foreach (var item in ItemsOf(project.Id))
            {
                if (Finds(search, item, inProject: false))
                {
                    found.Add(item);
                }
            }
        }

        total = found.Count;
        return
        [
            .. found
                .OrderBy(item => item.State == TaskState.Open ? 0 : 1)
                .ThenByDescending(item => item.CreatedAt, StringComparer.Ordinal)
                .ThenBy(item => item.Id, StringComparer.Ordinal)
                .Take(limit),
        ];
    }

    private sealed record Folded(string Title, string Notes, string Text);
}
