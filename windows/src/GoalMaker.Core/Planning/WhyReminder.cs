using System.Globalization;
using System.Text;

namespace GoalMaker.Core.Planning;

/// <summary>
/// The why reminder (docs/life-goals.md, M9-04), pinned by the 'why' groups and 'fnv1a' of
/// contracts/vectors/life-goals.json. Its moment is random within each period but worked out from the
/// period, so both devices agree without stored reminder rows; quiet hours hold it back like an
/// ordinary reminder.
/// </summary>
public static class WhyReminder
{
    private const int WindowMinutes = 600;
    private static readonly TimeOnly WindowStart = new(10, 0);

    /// <summary>The 32-bit FNV-1a hash of <paramref name="text"/>'s UTF-8 bytes.</summary>
    public static uint Fnv1a(string text)
    {
        var hash = 0x811C9DC5u;
        foreach (var value in Encoding.UTF8.GetBytes(text))
        {
            hash ^= value;
            hash = unchecked(hash * 0x01000193u);
        }

        return hash;
    }

    /// <summary>The index of the period <paramref name="day"/> falls in, counted from 1970-01-01 (weeks from Monday).</summary>
    public static long PeriodNumber(WhyFrequency frequency, DateOnly day)
    {
        long epochDay = day.DayNumber - DateOnly.FromDateTime(DateTime.UnixEpoch).DayNumber;
        return frequency == WhyFrequency.Weekly
            ? FloorDiv(epochDay + 3, 7)
            : FloorDiv(epochDay, WhyFrequencies.Days(frequency));
    }

    /// <summary>The first day of period <paramref name="number"/>.</summary>
    public static DateOnly PeriodStart(WhyFrequency frequency, long number)
    {
        var epochDay = frequency == WhyFrequency.Weekly ? (number * 7) - 3 : number * WhyFrequencies.Days(frequency);
        return DateOnly.FromDateTime(DateTime.UnixEpoch).AddDays((int)epochDay);
    }

    /// <summary>The moment of the period <paramref name="day"/> falls in. Not for <see cref="WhyFrequency.Off"/>.</summary>
    public static WhyMoment Moment(WhyFrequency frequency, DateOnly day, QuietHours quietHours) =>
        MomentOf(frequency, PeriodNumber(frequency, day), quietHours);

    /// <summary>The open life goal the period starting <paramref name="periodStart"/> shows; null with none open.</summary>
    public static LifeGoalItem? Goal(WhyFrequency frequency, DateOnly periodStart, IEnumerable<LifeGoalItem> goals)
    {
        var open = LifeGoalRules.OpenOnes(goals);
        if (open.Count == 0)
        {
            return null;
        }

        var index = PeriodNumber(frequency, periodStart) % open.Count;
        return open[(int)(index < 0 ? index + open.Count : index)];
    }

    /// <summary>What a look at <paramref name="now"/> shows: the latest moment after <paramref name="since"/> and up to now, if any.</summary>
    public static WhyMoment? Due(WhyFrequency frequency, QuietHours quietHours, DateTime since, DateTime now)
    {
        if (frequency == WhyFrequency.Off)
        {
            return null;
        }

        var current = PeriodNumber(frequency, DateOnly.FromDateTime(now));
        WhyMoment? latest = null;
        for (var number = current - 2; number <= current; number++)
        {
            var moment = MomentOf(frequency, number, quietHours);
            if (moment.At > since && moment.At <= now)
            {
                latest = moment;
            }
        }

        return latest;
    }

    /// <summary>The first moment after <paramref name="now"/>, for the timer; null when off.</summary>
    public static DateTime? Next(WhyFrequency frequency, QuietHours quietHours, DateTime now)
    {
        if (frequency == WhyFrequency.Off)
        {
            return null;
        }

        var current = PeriodNumber(frequency, DateOnly.FromDateTime(now));
        DateTime? first = null;
        for (var number = current - 1; number <= current + 2; number++)
        {
            var at = MomentOf(frequency, number, quietHours).At;
            if (at > now && (first is null || at < first))
            {
                first = at;
            }
        }

        return first;
    }

    private static WhyMoment MomentOf(WhyFrequency frequency, long number, QuietHours quietHours)
    {
        var start = PeriodStart(frequency, number);
        var seed = Fnv1a($"{WhyFrequencies.Key(frequency)}:{start.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture)}");
        var length = (uint)WhyFrequencies.Days(frequency);
        var at = start.AddDays((int)(seed % length))
            .ToDateTime(WindowStart)
            .AddMinutes(seed / length % WindowMinutes);
        return new WhyMoment(start, quietHours.Release(at, important: false));
    }

    private static long FloorDiv(long value, long divisor)
    {
        var quotient = value / divisor;
        return (value % divisor != 0 && (value < 0) != (divisor < 0)) ? quotient - 1 : quotient;
    }
}
