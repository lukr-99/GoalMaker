namespace GoalMaker.Core.Planning;

/// <summary>
/// The Focus Board: the open tasks the owner keeps an eye on, in the owner's order. A task is on the
/// board when its <see cref="TaskItem.FocusPosition"/> is set (contracts/vectors/focus.json).
/// </summary>
public static class FocusBoardRules
{
    /// <summary>The tasks on the board: open, not deleted, with a position; by position, then oldest first, then id.</summary>
    public static IReadOnlyList<TaskItem> Board(IEnumerable<TaskItem> tasks) =>
        tasks
            .Where(task => !task.Deleted && task.State == TaskState.Open && task.FocusPosition.HasValue)
            .OrderBy(task => task.FocusPosition!.Value)
            .ThenBy(task => task.CreatedAt, StringComparer.Ordinal)
            .ThenBy(task => task.Id, StringComparer.Ordinal)
            .ToList();

    /// <summary>The position a task added at the end gets: one more than the last one's, or 1 on an empty board.</summary>
    public static double AppendPosition(IEnumerable<TaskItem> tasks) =>
        (Board(tasks).LastOrDefault()?.FocusPosition ?? 0.0) + 1.0;

    /// <summary>A position between two neighbours, either of which can be missing (the top or the end).</summary>
    public static double Between(double? before, double? after) => (before, after) switch
    {
        (null, null) => 1.0,
        (null, { } next) => next - 1.0,
        ({ } previous, null) => previous + 1.0,
        ({ } previous, { } next) => (previous + next) / 2.0,
    };

    /// <summary>
    /// The position that puts <paramref name="taskId"/> at <paramref name="place"/> on the board, 1 being
    /// the top, with the task itself left out first. A place past the end is the end; one below 1 is the top.
    /// </summary>
    public static double PlaceAt(IEnumerable<TaskItem> tasks, string taskId, int place)
    {
        var others = Board(tasks).Where(task => task.Id != taskId).ToList();
        var index = Math.Clamp(place - 1, 0, others.Count);
        return Between(
            index > 0 ? others[index - 1].FocusPosition : null,
            index < others.Count ? others[index].FocusPosition : null);
    }
}
