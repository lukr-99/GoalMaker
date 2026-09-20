namespace GoalMaker.Core.Planning;

/// <summary>
/// The week and the month view (docs/calendar.md, contracts/vectors/calendar.json): which days a grid
/// covers, and what each day holds: the tasks planned for it, the deadlines falling on it, how many
/// reminders ring, and the days a repeating task would come round to.
/// </summary>
public static class CalendarRules
{
    public const string Week = "week";
    public const string Month = "month";

    // A repeat is followed at most this many times inside a range; a month grid is 42 days.
    private const int MaxRepeats = 60;

    /// <summary>The first day of the grid: the Monday of the week, or the Monday on or before the month's first.</summary>
    public static DateOnly Start(string kind, DateOnly day) =>
        Monday(kind == Month ? new DateOnly(day.Year, day.Month, 1) : day);

    /// <summary>The last day of the grid: the Sunday closing the week, or the one closing the month's last week.</summary>
    public static DateOnly End(string kind, DateOnly day) =>
        Monday(kind == Month ? new DateOnly(day.Year, day.Month, DateTime.DaysInMonth(day.Year, day.Month)) : day)
            .AddDays(6);

    /// <summary>Every day of the grid, in order, so a month is always whole weeks.</summary>
    public static IReadOnlyList<DateOnly> Days(string kind, DateOnly day)
    {
        var last = End(kind, day);
        var days = new List<DateOnly>();
        for (var current = Start(kind, day); current <= last; current = current.AddDays(1))
        {
            days.Add(current);
        }

        return days;
    }

    /// <summary>What each day from <paramref name="from"/> to <paramref name="to"/> holds, in order.</summary>
    public static IReadOnlyList<CalendarDay> Build(
        IReadOnlyList<TaskItem> tasks,
        IReadOnlyList<ReminderItem> reminders,
        DateOnly from,
        DateOnly to)
    {
        var live = tasks.Where(task => !task.Deleted).ToList();
        var byId = live.ToDictionary(task => task.Id, StringComparer.Ordinal);
        var planned = live
            .Where(task => task.State != TaskState.Dropped && task.PlannedDate is not null)
            .ToLookup(task => task.PlannedDate!.Value);
        var deadlines = live
            .Where(task => task.State == TaskState.Open && task.Deadline is not null)
            .ToLookup(task => task.Deadline!.Value);
        var ringing = reminders
            .Select(reminder => byId.TryGetValue(reminder.TaskId, out var task) ? ReminderRules.Due(reminder, task) : null)
            .Where(due => due is not null)
            .GroupBy(due => DateOnly.FromDateTime(due!.Value))
            .ToDictionary(group => group.Key, group => group.Count());
        var repeats = Repeats(live, from, to);

        var days = new List<CalendarDay>();
        for (var day = from; day <= to; day = day.AddDays(1))
        {
            days.Add(new CalendarDay(day)
            {
                Planned = Order(planned[day]),
                Deadlines = Order(deadlines[day]),
                Reminders = ringing.TryGetValue(day, out var count) ? count : 0,
                Repeats = Order(repeats.TryGetValue(day, out var found) ? found : []),
            });
        }

        return days;
    }

    // The days each repeating task would come round to inside the range, by day.
    private static Dictionary<DateOnly, List<TaskItem>> Repeats(IReadOnlyList<TaskItem> tasks, DateOnly from, DateOnly to)
    {
        // A day another occurrence of the same series is already planned for is that occurrence's, not a repeat.
        var taken = tasks
            .Where(task => task.PlannedDate is not null)
            .GroupBy(Occurrences.SeriesOf, StringComparer.Ordinal)
            .ToDictionary(group => group.Key, group => group.Select(task => task.PlannedDate!.Value).ToHashSet(), StringComparer.Ordinal);
        var found = new Dictionary<DateOnly, List<TaskItem>>();
        foreach (var task in tasks.Where(task => task.State == TaskState.Open && task.Recurrence is not null && task.PlannedDate is not null))
        {
            if (Recurrence.Parse(task.Recurrence) is not { } rule)
            {
                continue;
            }

            var series = taken.TryGetValue(Occurrences.SeriesOf(task), out var days) ? days : [];
            var day = task.PlannedDate!.Value;
            for (var step = 0; step < MaxRepeats; step++)
            {
                if (rule.Next(day, day) is not { } next || next > to)
                {
                    break;
                }

                day = next;
                if (day >= from && !series.Contains(day))
                {
                    if (!found.TryGetValue(day, out var list))
                    {
                        found[day] = list = [];
                    }

                    list.Add(task);
                }
            }
        }

        return found;
    }

    // Earliest time first, tasks without a time after them, then oldest first and by id.
    private static IReadOnlyList<TaskItem> Order(IEnumerable<TaskItem> tasks) =>
    [
        .. tasks
            .OrderBy(task => task.PlannedTime is null)
            .ThenBy(task => task.PlannedTime)
            .ThenBy(task => task.CreatedAt, StringComparer.Ordinal)
            .ThenBy(task => task.Id, StringComparer.Ordinal),
    ];

    private static DateOnly Monday(DateOnly day) => day.AddDays(-(((int)day.DayOfWeek + 6) % 7));
}
