namespace GoalMaker.Core.Planning;

/// <summary>
/// The reminders that call the owner to a review (docs/reviews.md, spec story 55, pinned by the
/// 'reviewReminders' cases in contracts/vectors/reminders.json). The weekly one rings on the weekday
/// the owner chose, the monthly one on the first day of a month, each once per planning day and only
/// while the review hasn't been written. Quiet hours don't move them: the owner picked the time.
/// </summary>
public static class ReviewReminder
{
    /// <summary>Sunday evening for the week, the first evening of a month for the month.</summary>
    public static readonly TimeOnly DefaultTime = new(18, 0);

    /// <summary>Sunday, as ISO counts weekdays (1 Monday to 7 Sunday).</summary>
    public const int DefaultWeekday = 7;

    // A monthly reminder can be up to a month away.
    private const int DaysAhead = 40;

    /// <summary>Whether a reminder of <paramref name="kind"/> falls on <paramref name="day"/>.</summary>
    public static bool IsReminderDay(string kind, DateOnly day, int weekday) =>
        kind == ReviewRules.Monthly ? day.Day == 1 : Weekday(day) == weekday;

    /// <summary>
    /// The period a reminder on <paramref name="day"/> looks back on: the week ending that weekend, the
    /// week just gone earlier in the week, or the month just gone.
    /// </summary>
    public static DateOnly PeriodStart(string kind, DateOnly day)
    {
        if (kind == ReviewRules.Monthly)
        {
            return new DateOnly(day.Year, day.Month, 1).AddMonths(-1);
        }

        var monday = day.AddDays(-(Weekday(day) - 1));
        return Weekday(day) >= 6 ? monday : monday.AddDays(-7);
    }

    /// <summary>The planning day whose reminder to show at <paramref name="now"/>.</summary>
    public static DateOnly? Due(
        string kind,
        TimeOnly? time,
        int weekday,
        int dayStartHour,
        IReadOnlySet<DateOnly> ran,
        DateTime since,
        DateTime now)
    {
        if (time is not { } at)
        {
            return null;
        }

        var today = PlanningDay.Of(now, dayStartHour);
        if (!IsReminderDay(kind, today, weekday) || ran.Contains(today))
        {
            return null;
        }

        var moment = RitualReminder.MomentOf(today, at, dayStartHour);
        return moment > since && moment <= now ? today : null;
    }

    /// <summary>The moment the alarm waits for after <paramref name="now"/>, or null when the reminder is off.</summary>
    public static DateTime? Next(string kind, TimeOnly? time, int weekday, int dayStartHour, IReadOnlySet<DateOnly> ran, DateTime now)
    {
        if (time is not { } at)
        {
            return null;
        }

        var day = PlanningDay.Of(now, dayStartHour);
        for (var step = 0; step < DaysAhead; step++)
        {
            var moment = RitualReminder.MomentOf(day, at, dayStartHour);
            if (IsReminderDay(kind, day, weekday) && !ran.Contains(day) && moment > now)
            {
                return moment;
            }

            day = day.AddDays(1);
        }

        return null;
    }

    /// <summary>Whether a reminder on screen for <paramref name="day"/> has to go.</summary>
    public static bool Stale(DateOnly day, int dayStartHour, IReadOnlySet<DateOnly> ran, DateTime now) =>
        ran.Contains(day) || PlanningDay.Of(now, dayStartHour) != day;

    // ISO weekday: 1 is Monday, 7 is Sunday.
    private static int Weekday(DateOnly day) => ((int)day.DayOfWeek + 6) % 7 + 1;
}
