namespace GoalMaker.Core.Planning;

/// <summary>
/// The one notification a day for wants that became ready (docs/wants.md, M8-05), pinned by the
/// 'notify' groups of contracts/vectors/wants.json. It rings at the owner's time and names the wants
/// that became ready since the last one; like the review reminders, quiet hours don't move it.
/// </summary>
public static class WantReminder
{
    /// <summary>Mid-morning, unless the owner picks another time.</summary>
    public static readonly TimeOnly DefaultTime = new(10, 0);

    /// <summary>What to show at <paramref name="now"/>: today's notification, when its moment came after <paramref name="since"/> and a want is newly ready.</summary>
    public static WantsDue? Due(TimeOnly? time, int dayStartHour, IReadOnlyList<WantItem> wants, DateTime since, DateTime now)
    {
        if (time is not { } at)
        {
            return null;
        }

        var today = PlanningDay.Of(now, dayStartHour);
        var moment = RitualReminder.MomentOf(today, at, dayStartHour);
        if (moment <= since || moment > now)
        {
            return null;
        }

        var ready = WantRules.Ready(wants, LastNotified(at, dayStartHour, since), today);
        return ready.Count == 0 ? null : new WantsDue(today, [.. ready.Select(want => want.Id)]);
    }

    /// <summary>The moment the alarm waits for after <paramref name="now"/>: the time on the first day an undecided want cools.</summary>
    public static DateTime? Next(TimeOnly? time, int dayStartHour, IReadOnlyList<WantItem> wants, DateTime now)
    {
        if (time is not { } at)
        {
            return null;
        }

        var today = PlanningDay.Of(now, dayStartHour);
        var moments = wants
            .Where(want => !want.Deleted && want.Decision is null && want.CoolsUntil >= today)
            .Select(want => RitualReminder.MomentOf(want.CoolsUntil, at, dayStartHour))
            .Where(moment => moment > now)
            .ToList();
        return moments.Count == 0 ? null : moments.Min();
    }

    /// <summary>Whether a notification naming <paramref name="shown"/> has to go: none of those wants is still waiting.</summary>
    public static bool Stale(IReadOnlyCollection<string> shown, IReadOnlyList<WantItem> wants) =>
        !wants.Any(want => shown.Contains(want.Id) && !want.Deleted && want.Decision is null);

    // The planning day of the last notification moment at or before since.
    private static DateOnly LastNotified(TimeOnly time, int dayStartHour, DateTime since)
    {
        var day = PlanningDay.Of(since, dayStartHour);
        return RitualReminder.MomentOf(day, time, dayStartHour) > since ? day.AddDays(-1) : day;
    }
}
