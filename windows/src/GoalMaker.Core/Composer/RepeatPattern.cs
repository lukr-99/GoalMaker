using System.Text;

namespace GoalMaker.Core.Composer;

/// <summary>
/// A parsed repeat before it's pinned to a planned date. <see cref="Days"/> or <see cref="MonthDay"/>
/// are null when the pattern takes them from the planned date (<c>weekly</c>, <c>every 2 weeks</c>, <c>monthly</c>).
/// </summary>
internal sealed record RepeatPattern(RepeatFrequency Frequency, int Interval = 1, IReadOnlySet<DayOfWeek>? Days = null, int? MonthDay = null)
{
    private static readonly DayOfWeek[] MondayFirst =
        [DayOfWeek.Monday, DayOfWeek.Tuesday, DayOfWeek.Wednesday, DayOfWeek.Thursday, DayOfWeek.Friday, DayOfWeek.Saturday, DayOfWeek.Sunday];

    /// <summary>The first day on or after <paramref name="earliest"/> this repeat falls on.</summary>
    public DateOnly FirstOccurrence(DateOnly earliest)
    {
        if (Frequency == RepeatFrequency.Weekly && Days is not null)
        {
            var day = earliest;
            while (!Days.Contains(day.DayOfWeek))
            {
                day = day.AddDays(1);
            }

            return day;
        }

        if (Frequency == RepeatFrequency.Monthly && MonthDay is { } monthDay)
        {
            for (var month = new DateOnly(earliest.Year, earliest.Month, 1); ; month = month.AddMonths(1))
            {
                if (monthDay <= DateTime.DaysInMonth(month.Year, month.Month) && month.AddDays(monthDay - 1) >= earliest)
                {
                    return month.AddDays(monthDay - 1);
                }
            }
        }

        return earliest;
    }

    /// <summary>The RRULE subset, with any missing weekday or day of month taken from <paramref name="planned"/>.</summary>
    public string Rule(DateOnly planned)
    {
        var rule = new StringBuilder("FREQ=").Append(Frequency.ToString().ToUpperInvariant());
        if (Interval > 1)
        {
            rule.Append(";INTERVAL=").Append(Interval);
        }

        if (Frequency == RepeatFrequency.Weekly)
        {
            var days = Days ?? new HashSet<DayOfWeek> { planned.DayOfWeek };
            rule.Append(";BYDAY=").AppendJoin(',', MondayFirst.Where(days.Contains).Select(day => day.ToString()[..2].ToUpperInvariant()));
        }
        else if (Frequency == RepeatFrequency.Monthly)
        {
            rule.Append(";BYMONTHDAY=").Append(MonthDay ?? planned.Day);
        }

        return rule.ToString();
    }
}
