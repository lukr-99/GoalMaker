namespace GoalMaker.Core.Planning;

/// <summary>
/// Calendar events (docs/calendar.md, contracts/vectors/calendar.json 'eventDays', 'bars', 'ongoing'
/// and 'picked'): the events a day holds, the bars a grid draws and the ones going on, what a pick of
/// days makes, and what a valid event is (the server's checks in migration 0026).
/// </summary>
public static class EventRules
{
    public const int MaxTitle = 200;
    public const int MaxNotes = 10000;

    /// <summary>The last day is at most this many days after the first.</summary>
    public const int MaxSpan = 366;

    /// <summary>Earliest first day first, then the longest, then by title, then by id; deleted ones left out.</summary>
    public static IReadOnlyList<EventItem> Order(IEnumerable<EventItem> events) =>
    [
        .. events
            .Where(item => !item.Deleted)
            .OrderBy(item => item.StartsOn)
            .ThenByDescending(item => item.Days)
            .ThenBy(item => item.Title, StringComparer.Ordinal)
            .ThenBy(item => item.Id, StringComparer.Ordinal),
    ];

    /// <summary>Whether the area and tag filter keeps an event: an area keeps its own events, a tag none, since events have no tags.</summary>
    public static bool Keeps(ListFilter filter, EventItem item) =>
        filter.TagId is null && (filter.AreaId is null || item.AreaId == filter.AreaId);

    /// <summary>The events each day from <paramref name="from"/> to <paramref name="to"/> holds, in order, narrowed by <paramref name="filter"/>.</summary>
    public static IReadOnlyDictionary<DateOnly, IReadOnlyList<EventItem>> Days(
        IEnumerable<EventItem> events,
        DateOnly from,
        DateOnly to,
        ListFilter? filter = null)
    {
        var kept = Order(events).Where(item => Keeps(filter ?? ListFilter.None, item)).ToList();
        var days = new Dictionary<DateOnly, IReadOnlyList<EventItem>>();
        for (var day = from; day <= to; day = day.AddDays(1))
        {
            days[day] = [.. kept.Where(item => Covers(item, day))];
        }

        return days;
    }

    /// <summary>
    /// The bars each week row of the grid from <paramref name="start"/> (a Monday) to
    /// <paramref name="end"/> (a Sunday) draws, a row's bars by lane and then by column. Lanes are given
    /// over the whole grid in day order, each event taking the lowest lane whose last event ended before
    /// its first day in the grid, so an event keeps its lane from row to row.
    /// </summary>
    public static IReadOnlyList<IReadOnlyList<EventBar>> Bars(IEnumerable<EventItem> events, DateOnly start, DateOnly end)
    {
        var laneEnds = new List<DateOnly>();
        var placed = new List<(EventItem Event, DateOnly From, DateOnly To, int Lane)>();
        foreach (var item in Order(events).Where(item => item.StartsOn <= end && item.EndsOn >= start))
        {
            var from = item.StartsOn < start ? start : item.StartsOn;
            var to = item.EndsOn > end ? end : item.EndsOn;
            var lane = laneEnds.FindIndex(last => last < from);
            if (lane < 0)
            {
                lane = laneEnds.Count;
                laneEnds.Add(to);
            }
            else
            {
                laneEnds[lane] = to;
            }

            placed.Add((item, from, to, lane));
        }

        var rows = new List<IReadOnlyList<EventBar>>();
        for (var monday = start; monday <= end; monday = monday.AddDays(7))
        {
            var sunday = monday.AddDays(6);
            rows.Add([
                .. placed
                    .Where(bar => bar.From <= sunday && bar.To >= monday)
                    .Select(bar => new EventBar(
                        bar.Event,
                        Math.Max(bar.From.DayNumber - monday.DayNumber, 0),
                        Math.Min(bar.To.DayNumber - monday.DayNumber, 6),
                        bar.Lane,
                        bar.Event.StartsOn < monday,
                        bar.Event.EndsOn > sunday))
                    .OrderBy(bar => bar.Lane)
                    .ThenBy(bar => bar.From),
            ]);
        }

        return rows;
    }

    /// <summary>The events <paramref name="day"/> falls inside, in order, with which of their days it is.</summary>
    public static IReadOnlyList<OngoingEvent> Ongoing(IEnumerable<EventItem> events, DateOnly day) =>
    [
        .. Order(events)
            .Where(item => Covers(item, day))
            .Select(item => new OngoingEvent(item, day.DayNumber - item.StartsOn.DayNumber + 1, item.Days)),
    ];

    /// <summary>
    /// The days one event made from a pick of days takes: the earliest picked day to the latest, gaps
    /// included. Null for no days, or when they are more than <see cref="MaxSpan"/> days apart.
    /// </summary>
    public static (DateOnly StartsOn, DateOnly EndsOn)? PickedEvent(IEnumerable<DateOnly> days)
    {
        var picked = days.ToList();
        if (picked.Count == 0)
        {
            return null;
        }

        var first = picked.Min();
        var last = picked.Max();
        return last.DayNumber - first.DayNumber > MaxSpan ? null : (first, last);
    }

    /// <summary>The days a copy of a task goes on for a pick of days: each picked day once, earliest first.</summary>
    public static IReadOnlyList<DateOnly> PickedTaskDays(IEnumerable<DateOnly> days) => [.. days.Distinct().Order()];

    /// <summary>
    /// The draft as it is stored, trimmed, with empty notes as none; null when the server would refuse
    /// it: no title or one over 200 characters, the last day before the first or more than 366 days
    /// after it, or notes over 10,000 characters.
    /// </summary>
    public static EventDraft? Check(EventDraft draft)
    {
        var title = draft.Title?.Trim() ?? string.Empty;
        var notes = string.IsNullOrWhiteSpace(draft.Notes) ? null : draft.Notes.Trim();
        var span = draft.EndsOn.DayNumber - draft.StartsOn.DayNumber;
        return title.Length is 0 or > MaxTitle || span is < 0 or > MaxSpan || notes?.Length > MaxNotes
            ? null
            : draft with { Title = title, Notes = notes };
    }

    private static bool Covers(EventItem item, DateOnly day) => item.StartsOn <= day && item.EndsOn >= day;
}
