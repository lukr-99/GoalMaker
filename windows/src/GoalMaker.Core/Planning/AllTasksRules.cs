namespace GoalMaker.Core.Planning;

/// <summary>
/// The All tasks place (docs/all-tasks.md): every open task in one list, grouped by area, so a task filed
/// into an area without a day can always be found. Pinned by contracts/vectors/all-tasks.json.
/// </summary>
public static class AllTasksRules
{
    /// <summary>The words of a search, folded the way <see cref="Matches"/> compares them.</summary>
    public static IReadOnlyList<string> Words(string query) =>
        [.. query.Split(' ').Select(ArchiveRules.Fold).Where(word => word.Length > 0)];

    /// <summary>Whether every one of <paramref name="words"/> is in the task's title or notes, ignoring case and accents.</summary>
    public static bool Matches(TaskItem task, IReadOnlyList<string> words)
    {
        if (words.Count == 0) return true;
        var text = ArchiveRules.Fold(task.Title) + "\n" + ArchiveRules.Fold(task.Notes);
        return words.All(word => text.Contains(word, StringComparison.Ordinal));
    }

    /// <summary>By date (the deadline, else the planned day), earliest first and undated last, then title, then id.</summary>
    public static IOrderedEnumerable<TaskItem> ByDate(IEnumerable<TaskItem> tasks) =>
        tasks
            .OrderBy(task => (task.Deadline ?? task.PlannedDate).HasValue ? 0 : 1)
            .ThenBy(task => task.Deadline ?? task.PlannedDate)
            .ThenBy(task => ArchiveRules.Fold(task.Title), StringComparer.Ordinal)
            .ThenBy(task => task.Id, StringComparer.Ordinal);

    /// <summary>
    /// The open tasks that match <paramref name="query"/>, grouped by area in the order of <paramref name="areaIds"/>,
    /// the no-area group last and empty groups left out. A project item shows only with <paramref name="projectItems"/>;
    /// a task's group is its own area, else its project's, and an area not in <paramref name="areaIds"/> counts as none.
    /// </summary>
    public static IReadOnlyList<TaskGroup> Groups(
        IEnumerable<TaskItem> tasks,
        IReadOnlyList<string> areaIds,
        IReadOnlyDictionary<string, string?> projectAreas,
        string query,
        bool projectItems)
    {
        var wanted = Words(query);
        var known = areaIds.ToHashSet(StringComparer.Ordinal);
        var shown = tasks
            .Where(task => !task.Deleted && task.State == TaskState.Open && (projectItems || task.ProjectId is null) && Matches(task, wanted))
            .ToList();
        string? GroupOf(TaskItem task)
        {
            var area = task.AreaId ?? (task.ProjectId is { } project ? projectAreas.GetValueOrDefault(project) : null);
            return area is not null && known.Contains(area) ? area : null;
        }

        var groups = new List<TaskGroup>();
        foreach (var area in areaIds.Cast<string?>().Append(null))
        {
            var inArea = shown.Where(task => GroupOf(task) == area).ToList();
            if (inArea.Count > 0) groups.Add(new TaskGroup(area, [.. ByDate(inArea)]));
        }

        return groups;
    }
}
