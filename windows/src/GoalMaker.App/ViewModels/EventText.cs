using System.Globalization;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>How a calendar event reads (docs/calendar.md): "12 to 15 October", "Prague, 12 to 15 October", "Prague · day 2 of 4".</summary>
public static class EventText
{
    /// <summary>
    /// The event's days: "12 October" for one day, "12 to 15 October" inside a month, "30 September to
    /// 2 October" across one, and with the years when it runs into another.
    /// </summary>
    public static string Days(EventItem item, IStrings strings)
    {
        var (first, last) = (item.StartsOn, item.EndsOn);
        if (first == last)
        {
            return Format(first, "d MMMM");
        }

        var (from, to) = first.Year != last.Year ? (Format(first, "d MMMM yyyy"), Format(last, "d MMMM yyyy"))
            : first.Month != last.Month ? (Format(first, "d MMMM"), Format(last, "d MMMM"))
            : (Format(first, "%d"), Format(last, "d MMMM"));
        return strings.Get("Calendar.EventSpan", from, to);
    }

    /// <summary>What a screen reader says for an event's bar or line: "Prague, 12 to 15 October".</summary>
    public static string Name(EventItem item, IStrings strings) => strings.Get("Calendar.EventName", item.Title, Days(item, strings));

    /// <summary>Today's line for an event going on: "Prague · day 2 of 4", or just the title for a one-day event.</summary>
    public static string Ongoing(OngoingEvent ongoing, IStrings strings) =>
        ongoing.Days > 1 ? strings.Get("Lists.EventDay", ongoing.Event.Title, ongoing.DayOf, ongoing.Days) : ongoing.Event.Title;

    private static string Format(DateOnly day, string format) => day.ToString(format, CultureInfo.CurrentCulture);
}
