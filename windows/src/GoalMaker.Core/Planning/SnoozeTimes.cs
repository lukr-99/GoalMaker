namespace GoalMaker.Core.Planning;

/// <summary>When a snoozed reminder comes back (docs/reminders.md).</summary>
public static class SnoozeTimes
{
    /// <summary>The hour "tomorrow morning" means.</summary>
    public const int MorningHour = 8;

    /// <summary>
    /// When the reminder comes back. "Tomorrow morning" counts from the planning day, so snoozing
    /// before the day starts brings it back that same morning.
    /// </summary>
    public static DateTime Target(this Snooze option, DateTime now, int dayStartHour = PlanningDay.DefaultStartHour) => option switch
    {
        Snooze.TenMinutes => now.AddMinutes(10),
        Snooze.OneHour => now.AddHours(1),
        Snooze.TomorrowMorning => PlanningDay.Of(now, dayStartHour).AddDays(1).ToDateTime(new TimeOnly(MorningHour, 0)),
        _ => throw new ArgumentOutOfRangeException(nameof(option), option, "Unknown snooze option."),
    };
}
