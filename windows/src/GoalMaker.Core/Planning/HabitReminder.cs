namespace GoalMaker.Core.Planning;

/// <summary>
/// A habit's reminder (docs/reminders.md, pinned by 'habitReminders' and 'habitReminderStale' in
/// contracts/vectors/reminders.json). It rings once per planning day at the habit's
/// <see cref="HabitItem.RemindAt"/>, the way the rituals' reminders do, but only while the habit is still
/// left that day: done, skipped, failed, paused, not due, not started or a limit, it stays quiet. Quiet
/// hours don't move it.
/// </summary>
public static class HabitReminder
{
    private const int DaysAhead = 7;

    /// <summary>Today's planning day when the habit's reminder came after <paramref name="since"/> and by <paramref name="now"/> and the habit is still left.</summary>
    public static DateOnly? Due(
        HabitItem habit,
        IReadOnlyList<HabitCheckin> checkins,
        IReadOnlyList<HabitPause> pauses,
        int dayStartHour,
        DateTime since,
        DateTime now)
    {
        if (habit.RemindAt is not { } time)
        {
            return null;
        }

        var today = PlanningDay.Of(now, dayStartHour);
        var moment = RitualReminder.MomentOf(today, time, dayStartHour);
        return moment > since && moment <= now && Left(habit, today, checkins, pauses) ? today : null;
    }

    /// <summary>The first moment after <paramref name="now"/>, within seven planning days, of a day the habit is left; null for none.</summary>
    public static DateTime? Next(HabitItem habit, IReadOnlyList<HabitCheckin> checkins, IReadOnlyList<HabitPause> pauses, int dayStartHour, DateTime now)
    {
        if (habit.RemindAt is not { } time)
        {
            return null;
        }

        var day = PlanningDay.Of(now, dayStartHour);
        for (var ahead = 0; ahead < DaysAhead; ahead++, day = day.AddDays(1))
        {
            var moment = RitualReminder.MomentOf(day, time, dayStartHour);
            if (moment > now && Left(habit, day, checkins, pauses))
            {
                return moment;
            }
        }

        return null;
    }

    /// <summary>Whether a reminder on screen for <paramref name="day"/> has to go: the planning day moved on, or the habit is no longer left.</summary>
    public static bool Stale(HabitItem habit, DateOnly day, IReadOnlyList<HabitCheckin> checkins, IReadOnlyList<HabitPause> pauses, int dayStartHour, DateTime now) =>
        habit.Deleted || habit.RemindAt is null || PlanningDay.Of(now, dayStartHour) != day || !Left(habit, day, checkins, pauses);

    private static bool Left(HabitItem habit, DateOnly day, IReadOnlyList<HabitCheckin> checkins, IReadOnlyList<HabitPause> pauses) =>
        !habit.Deleted && HabitRules.Standing(habit, day, checkins, pauses) == HabitStanding.Left;
}
