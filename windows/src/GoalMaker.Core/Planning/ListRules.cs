namespace GoalMaker.Core.Planning;

/// <summary>Which open tasks each list shows, in what order (docs/lists.md, contracts/vectors/lists.json).</summary>
public static class ListRules
{
    public static PlanningLists Lists(IEnumerable<TaskItem> tasks, DateOnly today)
    {
        var live = tasks.Where(task => !task.Deleted).ToList();
        var open = live.Where(task => task.State == TaskState.Open).ToList();
        var planned = open.Where(task => task.PlannedDate == today).ToList();
        var tomorrow = today.AddDays(1);
        var counted = live.Where(task => task.PlannedDate == today && task.State != TaskState.Dropped).ToList();
        return new PlanningLists(
            today,
            new TodaySections(
                ByTime(planned.Where(task => task.TopPriority)),
                ByTime(planned.Where(task => !task.TopPriority && task.PlannedTime is not null)),
                ByCreation(planned.Where(task => !task.TopPriority && task.PlannedTime is null)),
                [.. open.Where(task => task.PlannedDate < today)
                    .OrderBy(task => task.PlannedDate)
                    .ThenBy(task => task.PlannedTime is null)
                    .ThenBy(task => task.PlannedTime)
                    .ThenBy(task => task.CreatedAt, StringComparer.Ordinal)
                    .ThenBy(task => task.Id, StringComparer.Ordinal)]),
            [.. open.Where(task => task.PlannedDate == tomorrow)
                .OrderByDescending(task => task.TopPriority)
                .ThenBy(task => task.PlannedTime is null)
                .ThenBy(task => task.PlannedTime)
                .ThenBy(task => task.CreatedAt, StringComparer.Ordinal)
                .ThenBy(task => task.Id, StringComparer.Ordinal)],
            ByCreation(open.Where(task => task.PlannedDate is null && task.AreaId is null)),
            new DaySummary(counted.Count(task => task.State == TaskState.Done), counted.Count));
    }

    // Untimed last, then creation, then id, so both apps agree on ties.
    private static List<TaskItem> ByTime(IEnumerable<TaskItem> tasks) =>
        [.. tasks.OrderBy(task => task.PlannedTime is null)
            .ThenBy(task => task.PlannedTime)
            .ThenBy(task => task.CreatedAt, StringComparer.Ordinal)
            .ThenBy(task => task.Id, StringComparer.Ordinal)];

    private static List<TaskItem> ByCreation(IEnumerable<TaskItem> tasks) =>
        [.. tasks.OrderBy(task => task.CreatedAt, StringComparer.Ordinal).ThenBy(task => task.Id, StringComparer.Ordinal)];
}
