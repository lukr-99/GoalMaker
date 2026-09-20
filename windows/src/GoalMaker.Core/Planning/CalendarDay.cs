namespace GoalMaker.Core.Planning;

/// <summary>
/// One day of the calendar (docs/calendar.md): what is planned for it, what is due on it, how many
/// reminders ring, and the repeating tasks that would come round to it.
/// </summary>
public sealed record CalendarDay(DateOnly Day)
{
    public IReadOnlyList<TaskItem> Planned { get; init; } = [];

    public IReadOnlyList<TaskItem> Deadlines { get; init; } = [];

    public int Reminders { get; init; }

    public IReadOnlyList<TaskItem> Repeats { get; init; } = [];

    /// <summary>Whether the day has nothing on it at all.</summary>
    public bool Empty => Planned.Count == 0 && Deadlines.Count == 0 && Reminders == 0 && Repeats.Count == 0;

    /// <summary>How many things the day holds, for the dot or the count a month cell shows.</summary>
    public int Count => Planned.Count + Deadlines.Count + Repeats.Count;

    /// <summary>How many of the day's planned tasks are done, so a cell can show what is left.</summary>
    public int Done => Planned.Count(task => task.State == TaskState.Done);
}
