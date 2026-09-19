using GoalMaker.Core.Backend;
using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Settings;

/// <summary>
/// Device-local settings that are not synced: the appearance, when the planning day starts, quiet
/// hours, the evening reminder, the sidebar and, in dev builds, a backend override.
/// </summary>
public interface ISettingsStore
{
    Appearance Appearance { get; set; }

    /// <summary>The hour the planning day starts (docs/lists.md), 0 to 6; 4 unless changed.</summary>
    int DayStartHour { get; set; }

    /// <summary>The window that holds ordinary reminders back (docs/reminders.md); off unless set.</summary>
    QuietHours QuietHours { get; set; }

    /// <summary>When the evening Plan tomorrow reminder rings (docs/reminders.md); 20:00 unless changed, null when off.</summary>
    TimeOnly? PlanTomorrowReminder { get; set; }

    /// <summary>When the weekly review reminder rings and on which weekday (1 Monday to 7 Sunday); null when off.</summary>
    TimeOnly? WeeklyReviewReminder { get; set; }

    int WeeklyReviewWeekday { get; set; }

    /// <summary>When the monthly review reminder rings, on the first day of a month; null when off.</summary>
    TimeOnly? MonthlyReviewReminder { get; set; }

    /// <summary>When this device last looked at its reminders, so each one is shown once (docs/reminders.md).</summary>
    DateTimeOffset? RemindedUntil { get; set; }

    /// <summary>The global quick-add shortcut as text (<c>Win+Alt+Space</c>); null for the default, empty for none.</summary>
    string? QuickAddHotkey { get; set; }

    /// <summary>Whether the sidebar is collapsed to icons.</summary>
    bool NavigationCollapsed { get; set; }

    /// <summary>Dev builds only: another Supabase project to use from the next app start.</summary>
    BackendEnvironment? BackendOverride { get; set; }

    /// <summary>The main window's last position, restored at start-up when it still fits a screen.</summary>
    WindowPlacement? MainWindowPlacement { get; set; }
}
