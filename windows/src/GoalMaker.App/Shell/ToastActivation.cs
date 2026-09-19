using System.Globalization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Shell;

/// <summary>
/// A click on a reminder toast, carried in the toast's arguments as
/// <c>action=snooze;reminder=&lt;id&gt;;snooze=TenMinutes</c>. For the evening Plan tomorrow
/// reminder the id is its planning day, <c>action=SkipPlan;reminder=2026-09-18</c>.
/// </summary>
public sealed record ToastActivation(ToastAction Action, string ReminderId, Snooze? Snooze = null)
{
    /// <summary>The planning day of an evening reminder's click, or null for a task reminder's.</summary>
    public DateOnly? PlanDay =>
        Action is ToastAction.Plan or ToastAction.SkipPlan
        && DateOnly.TryParseExact(ReminderId, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out var day)
            ? day
            : null;

    /// <summary>A review reminder's click: its ritual, the planning day it rang on, and the period it reviews.</summary>
    public (string Ritual, DateOnly Day) Review()
    {
        var parts = ReminderId.Split('/');
        return parts.Length == 2
            && DateOnly.TryParseExact(parts[1], "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out var day)
                ? (parts[0], day)
                : (string.Empty, DateOnly.MinValue);
    }

    /// <summary>The arguments a toast or one of its buttons carries.</summary>
    public string Arguments => Snooze is { } option
        ? $"action={Action};reminder={ReminderId};snooze={option}"
        : $"action={Action};reminder={ReminderId}";

    /// <summary>Reads <paramref name="arguments"/> back, or null when they aren't a reminder's.</summary>
    public static ToastActivation? Parse(string? arguments)
    {
        if (string.IsNullOrEmpty(arguments))
        {
            return null;
        }

        var parts = arguments.Split(';', StringSplitOptions.RemoveEmptyEntries)
            .Select(part => part.Split('=', 2))
            .Where(pair => pair.Length == 2)
            .ToDictionary(pair => pair[0], pair => pair[1], StringComparer.Ordinal);
        if (!parts.TryGetValue("action", out var action) || !Enum.TryParse<ToastAction>(action, out var kind)
            || !parts.TryGetValue("reminder", out var reminder) || reminder.Length == 0)
        {
            return null;
        }

        Snooze? snooze = parts.TryGetValue("snooze", out var option) && Enum.TryParse<Snooze>(option, out var parsed) ? parsed : null;
        return kind == ToastAction.Snooze && snooze is null ? null : new ToastActivation(kind, reminder, snooze);
    }
}
