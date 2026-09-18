using System.Globalization;

namespace GoalMaker.Core.Planning;

/// <summary>
/// A repeat rule the apps can follow (docs/repeating.md): the RRULE subset the composer writes.
/// <see cref="Days"/> and <see cref="MonthDay"/> are null when the rule takes them from the anchor.
/// </summary>
public sealed record Recurrence(RecurrenceFrequency Frequency, int Interval, IReadOnlySet<DayOfWeek>? Days, int? MonthDay)
{
    // Ten years of days: far past any rule's next match; a rule that finds nothing in it has none.
    private const int SearchDays = 3700;

    private static readonly Dictionary<string, DayOfWeek> Weekdays = new(StringComparer.Ordinal)
    {
        ["MO"] = DayOfWeek.Monday,
        ["TU"] = DayOfWeek.Tuesday,
        ["WE"] = DayOfWeek.Wednesday,
        ["TH"] = DayOfWeek.Thursday,
        ["FR"] = DayOfWeek.Friday,
        ["SA"] = DayOfWeek.Saturday,
        ["SU"] = DayOfWeek.Sunday,
    };

    private static readonly HashSet<string> Parts = new(StringComparer.Ordinal) { "FREQ", "INTERVAL", "BYDAY", "BYMONTHDAY" };

    /// <summary>The rule in <paramref name="text"/>, or null when it isn't one the apps can follow.</summary>
    public static Recurrence? Parse(string? text)
    {
        if (string.IsNullOrWhiteSpace(text))
        {
            return null;
        }

        var parts = new Dictionary<string, string>(StringComparer.Ordinal);
        foreach (var part in text.Trim().ToUpperInvariant().Split(';'))
        {
            var pair = part.Split('=', 2);
            if (pair.Length != 2 || !Parts.Contains(pair[0]) || !parts.TryAdd(pair[0], pair[1]))
            {
                return null;
            }
        }

        RecurrenceFrequency? frequency = parts.GetValueOrDefault("FREQ") switch
        {
            "DAILY" => RecurrenceFrequency.Daily,
            "WEEKLY" => RecurrenceFrequency.Weekly,
            "MONTHLY" => RecurrenceFrequency.Monthly,
            _ => null,
        };
        if (frequency is not { } found)
        {
            return null;
        }

        var interval = 1;
        if (parts.TryGetValue("INTERVAL", out var intervalText)
            && (!int.TryParse(intervalText, NumberStyles.None, CultureInfo.InvariantCulture, out interval) || interval < 1))
        {
            return null;
        }

        HashSet<DayOfWeek>? days = null;
        if (parts.TryGetValue("BYDAY", out var dayText))
        {
            if (found != RecurrenceFrequency.Weekly)
            {
                return null;
            }

            days = [];
            foreach (var code in dayText.Split(','))
            {
                if (!Weekdays.TryGetValue(code, out var day))
                {
                    return null;
                }

                days.Add(day);
            }
        }

        int? monthDay = null;
        if (parts.TryGetValue("BYMONTHDAY", out var monthDayText))
        {
            if (found != RecurrenceFrequency.Monthly
                || !int.TryParse(monthDayText, NumberStyles.None, CultureInfo.InvariantCulture, out var value)
                || value is < 1 or > 31)
            {
                return null;
            }

            monthDay = value;
        }

        return new Recurrence(found, interval, days, monthDay);
    }

    /// <summary>
    /// The next occurrence's day: the first match after the later of <paramref name="planned"/> and
    /// <paramref name="today"/>, counting from <paramref name="planned"/> (today when there is none).
    /// </summary>
    public DateOnly? Next(DateOnly? planned, DateOnly today)
    {
        var anchor = planned ?? today;
        var day = anchor > today ? anchor : today;
        for (var step = 0; step < SearchDays; step++)
        {
            day = day.AddDays(1);
            if (Matches(day, anchor))
            {
                return day;
            }
        }

        return null;
    }

    private bool Matches(DateOnly day, DateOnly anchor) => Frequency switch
    {
        RecurrenceFrequency.Daily => (day.DayNumber - anchor.DayNumber) % Interval == 0,
        RecurrenceFrequency.Weekly => (Monday(day).DayNumber - Monday(anchor).DayNumber) / 7 % Interval == 0
            && (Days?.Contains(day.DayOfWeek) ?? day.DayOfWeek == anchor.DayOfWeek),
        _ => (((day.Year - anchor.Year) * 12) + day.Month - anchor.Month) % Interval == 0
            && day.Day == (MonthDay ?? anchor.Day),
    };

    private static DateOnly Monday(DateOnly day) => day.AddDays(-(((int)day.DayOfWeek + 6) % 7));
}
