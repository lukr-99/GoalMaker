namespace GoalMaker.Core.Planning;

/// <summary>
/// The evening Plan tomorrow reminder (docs/reminders.md, pinned by the 'ritual' cases in
/// contracts/vectors/reminders.json). It rings once per planning day at the time the owner chose,
/// unless the ritual was already done or skipped that day on either device. Quiet hours don't move it:
/// the owner picked the time.
/// </summary>
public static class RitualReminder
{
    private const int DaysAhead = 3;

    /// <summary>20:00, until the owner picks another time or switches it off.</summary>
    public static TimeOnly DefaultTime { get; } = new(20, 0);

    /// <summary>The first moment of planning day <paramref name="day"/> whose clock reads <paramref name="time"/>: after midnight when the time comes before the day's start.</summary>
    public static DateTime MomentOf(DateOnly day, TimeOnly time, int dayStartHour) =>
        time.Hour >= dayStartHour ? day.ToDateTime(time) : day.AddDays(1).ToDateTime(time);

    /// <summary>The planning day whose reminder to show at <paramref name="now"/>: today's, when its moment came after <paramref name="since"/>. Never an earlier day.</summary>
    public static DateOnly? Due(TimeOnly? time, int dayStartHour, IReadOnlySet<DateOnly> ran, DateTime since, DateTime now)
    {
        if (time is not { } at)
        {
            return null;
        }

        var today = PlanningDay.Of(now, dayStartHour);
        var moment = MomentOf(today, at, dayStartHour);
        return !ran.Contains(today) && moment > since && moment <= now ? today : null;
    }

    /// <summary>The moment the alarm waits for after <paramref name="now"/>, or null when the reminder is off.</summary>
    public static DateTime? Next(TimeOnly? time, int dayStartHour, IReadOnlySet<DateOnly> ran, DateTime now)
    {
        if (time is not { } at)
        {
            return null;
        }

        var day = PlanningDay.Of(now, dayStartHour);
        for (var step = 0; step < DaysAhead; step++, day = day.AddDays(1))
        {
            var moment = MomentOf(day, at, dayStartHour);
            if (!ran.Contains(day) && moment > now)
            {
                return moment;
            }
        }

        return null;
    }

    /// <summary>Whether a reminder on screen for <paramref name="day"/> has to go: the ritual ran, or the planning day moved on.</summary>
    public static bool Stale(DateOnly day, int dayStartHour, IReadOnlySet<DateOnly> ran, DateTime now) =>
        ran.Contains(day) || PlanningDay.Of(now, dayStartHour) != day;
}
