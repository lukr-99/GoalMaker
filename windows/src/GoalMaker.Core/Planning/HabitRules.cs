using System.Globalization;

namespace GoalMaker.Core.Planning;

/// <summary>
/// Habit cadences, periods, streaks, the heatmap and today's ring (docs/habits.md,
/// contracts/vectors/habits.json). The check-ins and pauses handed in are the habit's own.
/// </summary>
public static class HabitRules
{
    public const string Daily = "daily";
    public const string OnWeekdays = "weekdays";
    public const string PerWeek = "per_week";
    public const string PerMonth = "per_month";
    public const string Check = "check";
    public const string Count = "count";
    public const string Amount = "amount";
    public const string AtLeast = "at_least";
    public const string AtMost = "at_most";
    private const string Namespace = "b8c61b22-5e0c-4f0a-9d1e-6f4a2c7e3b91";

    // A daily habit's streak can't reach back further than this many periods.
    private const int MaxPeriods = 3700;

    /// <summary>The weekday bit of <paramref name="day"/>: Monday 1, Tuesday 2 ... Sunday 64.</summary>
    public static int WeekdayBit(DateOnly day) => 1 << (((int)day.DayOfWeek + 6) % 7);

    /// <summary>Whether <paramref name="habit"/> is due on <paramref name="day"/>; weekly and monthly habits are due any day.</summary>
    public static bool IsDue(HabitItem habit, DateOnly day) =>
        habit.Cadence != OnWeekdays || ((habit.Weekdays ?? 0) & WeekdayBit(day)) != 0;

    /// <summary>The first day of the habit's period holding <paramref name="day"/>: the day, its week's Monday, or its month's first.</summary>
    public static DateOnly PeriodStart(HabitItem habit, DateOnly day) => habit.Cadence switch
    {
        PerWeek => day.AddDays(-(((int)day.DayOfWeek + 6) % 7)),
        PerMonth => new DateOnly(day.Year, day.Month, 1),
        _ => day,
    };

    /// <summary>The last day of the habit's period starting on <paramref name="start"/>.</summary>
    public static DateOnly PeriodEnd(HabitItem habit, DateOnly start) => habit.Cadence switch
    {
        PerWeek => start.AddDays(6),
        PerMonth => start.AddMonths(1).AddDays(-1),
        _ => start,
    };

    /// <summary>Whether the habit's number is a limit rather than something to reach (docs/habits.md).</summary>
    public static bool IsLimit(HabitItem habit) => habit.Direction == AtMost;

    /// <summary>A limit habit's number: the target, or none at all for a check ("not once").</summary>
    public static double Limit(HabitItem habit) => habit.Measure == Check ? 0 : habit.Target ?? 0;

    /// <summary>Whether a value goes over a limit habit's number. A habit to build is never over.</summary>
    public static bool IsOver(HabitItem habit, double value) => IsLimit(habit) && value > Limit(habit);

    /// <summary>Whether the day went over the limit: what turns the ring and the day red.</summary>
    public static bool WentOver(HabitItem habit, DateOnly day, IReadOnlyList<HabitCheckin> checkins) =>
        IsLimit(habit) && checkins.Any(checkin => !checkin.Deleted && !checkin.Skipped && checkin.Day == day && IsOver(habit, checkin.Value));

    /// <summary>
    /// Whether a check-in meets its day: checked, or the day's value reaching the target. Under a limit,
    /// a day nobody logged is met, because nothing was had. Skipped never meets a day.
    /// </summary>
    public static bool DayMet(HabitItem habit, HabitCheckin? checkin)
    {
        if (IsLimit(habit))
        {
            return checkin is null || checkin.Deleted || (!checkin.Skipped && !IsOver(habit, checkin.Value));
        }

        if (checkin is null || checkin.Deleted || checkin.Skipped)
        {
            return false;
        }

        return habit.Measure == Check ? checkin.Value >= 1 : checkin.Value >= (habit.Target ?? double.MaxValue);
    }

    /// <summary>How many days a period needs: one for a day, N for a week or month.</summary>
    public static int Required(HabitItem habit) => habit.Cadence is PerWeek or PerMonth ? habit.Times ?? 1 : 1;

    /// <summary>The state of the habit's period starting on <paramref name="start"/>, seen from <paramref name="today"/>.</summary>
    public static HabitPeriodState State(HabitItem habit, DateOnly start, DateOnly today, IReadOnlyList<HabitCheckin> checkins, IReadOnlyList<HabitPause> pauses)
    {
        var end = PeriodEnd(habit, start);
        if (end < habit.StartsOn)
        {
            return HabitPeriodState.None;
        }

        if (habit.Cadence is Daily or OnWeekdays && !IsDue(habit, start))
        {
            return HabitPeriodState.None;
        }

        var inPeriod = checkins.Where(checkin => !checkin.Deleted && checkin.Day >= start && checkin.Day <= end).ToList();
        if (IsLimit(habit))
        {
            // A limit is kept by default, so a pause or a skip comes before the day is judged, and a
            // day over the number is missed the moment it happens, today included.
            if (pauses.Any(pause => Covers(pause, start, end)))
            {
                return HabitPeriodState.Paused;
            }

            if (inPeriod.Any(checkin => checkin.Skipped))
            {
                return HabitPeriodState.Skipped;
            }

            if (inPeriod.Any(checkin => IsOver(habit, checkin.Value)))
            {
                return HabitPeriodState.Missed;
            }

            return end >= today ? HabitPeriodState.Open : HabitPeriodState.Met;
        }

        if (inPeriod.Count(checkin => DayMet(habit, checkin)) >= Required(habit))
        {
            return HabitPeriodState.Met;
        }

        if (pauses.Any(pause => Covers(pause, start, end)))
        {
            return HabitPeriodState.Paused;
        }

        if (inPeriod.Any(checkin => checkin.Skipped))
        {
            return HabitPeriodState.Skipped;
        }

        return end >= today ? HabitPeriodState.Open : HabitPeriodState.Missed;
    }

    /// <summary>Met periods back from the one holding <paramref name="today"/>; open, paused, skipped and none pass, missed ends it.</summary>
    public static int Streak(HabitItem habit, DateOnly today, IReadOnlyList<HabitCheckin> checkins, IReadOnlyList<HabitPause> pauses)
    {
        var count = 0;
        var start = PeriodStart(habit, today);
        for (var period = 0; period < MaxPeriods && PeriodEnd(habit, start) >= habit.StartsOn; period++)
        {
            switch (State(habit, start, today, checkins, pauses))
            {
                case HabitPeriodState.Met:
                    count++;
                    break;
                case HabitPeriodState.Missed:
                    return count;
            }

            start = PeriodStart(habit, start.AddDays(-1));
        }

        return count;
    }

    /// <summary>The starts of the habit's periods that touch the days <paramref name="from"/> to <paramref name="to"/>, oldest first.</summary>
    public static IEnumerable<DateOnly> PeriodsBetween(HabitItem habit, DateOnly from, DateOnly to)
    {
        var start = PeriodStart(habit, from);
        while (start <= to)
        {
            if (start >= from || PeriodEnd(habit, start) >= from)
            {
                yield return start;
            }

            start = PeriodEnd(habit, start).AddDays(1);
        }
    }

    /// <summary>A day of the heatmap: none, paused, skipped, over a limit, or the day's value against its target.</summary>
    public static HabitHeat Heat(HabitItem habit, DateOnly day, IReadOnlyList<HabitCheckin> checkins, IReadOnlyList<HabitPause> pauses)
    {
        if (day < habit.StartsOn || !IsDue(habit, day))
        {
            return HabitHeat.None;
        }

        if (pauses.Any(pause => Covers(pause, day, day)))
        {
            return HabitHeat.Paused;
        }

        var checkin = checkins.FirstOrDefault(checkin => !checkin.Deleted && checkin.Day == day);
        if (checkin?.Skipped == true)
        {
            return HabitHeat.Skipped;
        }

        if (IsLimit(habit))
        {
            // A limit's heatmap reads the other way round: a clean day is full, and going over is its own mark.
            return IsOver(habit, checkin?.Value ?? 0) ? HabitHeat.Over : HabitHeat.Share(1 - Share(habit, checkin?.Value ?? 0));
        }

        return HabitHeat.Share(Share(habit, checkin?.Value ?? 0));
    }

    /// <summary>Today's ring: the day against the target, or the days met so far against N; null when today isn't due.</summary>
    public static double? Ring(HabitItem habit, DateOnly today, IReadOnlyList<HabitCheckin> checkins)
    {
        if (habit.Cadence is PerWeek or PerMonth)
        {
            var start = PeriodStart(habit, today);
            var end = PeriodEnd(habit, start);
            var met = checkins.Count(checkin => checkin.Day >= start && checkin.Day <= end && DayMet(habit, checkin));
            return Math.Min(1.0, (double)met / Required(habit));
        }

        if (!IsDue(habit, today))
        {
            return null;
        }

        var todays = checkins.FirstOrDefault(checkin => !checkin.Deleted && !checkin.Skipped && checkin.Day == today);
        return Share(habit, todays?.Value ?? 0);
    }

    /// <summary>The id of a habit's one check-in on <paramref name="day"/>, the same on every device.</summary>
    public static string CheckinId(string habitId, DateOnly day) =>
        NameBasedUuid.Of(Namespace, $"checkin/{habitId.ToLowerInvariant()}/{day.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture)}");

    /// <summary>
    /// The check-in values that count toward <paramref name="goal"/>: of habits serving it, measured by count
    /// or amount, in its unit (lowercased, without spaces), not skipped, on a day in its period (story 32).
    /// </summary>
    public static IReadOnlyList<double> GoalAmounts(GoalItem goal, IEnumerable<HabitItem> habits, IEnumerable<HabitCheckin> checkins)
    {
        if (goal.Unit is null)
        {
            return [];
        }

        var unit = UnitKey(goal.Unit);
        var end = GoalRules.PeriodEnd(goal.Horizon, goal.PeriodStart);
        var serving = habits
            .Where(habit => !habit.Deleted && habit.GoalId == goal.Id && habit.Measure != Check && habit.Unit is not null && UnitKey(habit.Unit) == unit)
            .Select(habit => habit.Id)
            .ToHashSet();
        return checkins
            .Where(checkin => !checkin.Deleted && !checkin.Skipped && serving.Contains(checkin.HabitId) && checkin.Day >= goal.PeriodStart && checkin.Day <= end)
            .Select(checkin => checkin.Value)
            .ToList();
    }

    private static bool Covers(HabitPause pause, DateOnly start, DateOnly end) =>
        !pause.Deleted && pause.From <= end && (pause.Until is null || pause.Until >= start);

    private static double Share(HabitItem habit, double value) =>
        habit.Measure == Check ? (value >= 1 ? 1 : 0) : Math.Clamp(value / (habit.Target ?? 1), 0, 1);

    private static string UnitKey(string unit) => string.Concat(unit.Where(character => !char.IsWhiteSpace(character))).ToLowerInvariant();
}
