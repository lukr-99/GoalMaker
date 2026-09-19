using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.Infrastructure.Settings;

/// <summary>The settings file's shape. Version 1; fields added in M2 (appearance, quiet hours) read as defaults from older files.</summary>
public sealed record SettingsDocument
{
    public int Version { get; init; } = 1;

    public string? ThemeId { get; init; }

    public ThemeMode ThemeMode { get; init; } = ThemeMode.System;

    public bool PureBlack { get; init; }

    public ReduceMotion ReduceMotion { get; init; } = ReduceMotion.System;

    public bool CompletionSound { get; init; }

    public int DayStartHour { get; init; } = PlanningDay.DefaultStartHour;

    public TimeOnly QuietHoursStart { get; init; }

    public TimeOnly QuietHoursEnd { get; init; }

    /// <summary>The evening reminder's time; missing from older files reads as 20:00, null means off.</summary>
    public TimeOnly? PlanTomorrowReminder { get; init; } = RitualReminder.DefaultTime;

    public TimeOnly? WeeklyReviewReminder { get; init; } = ReviewReminder.DefaultTime;

    public int WeeklyReviewWeekday { get; init; } = ReviewReminder.DefaultWeekday;

    public TimeOnly? MonthlyReviewReminder { get; init; } = ReviewReminder.DefaultTime;

    public DateTimeOffset? RemindedUntil { get; init; }

    public string? QuickAddHotkey { get; init; }

    public bool NavigationCollapsed { get; init; }

    public string? BackendUrl { get; init; }

    public string? BackendKey { get; init; }

    public WindowPlacement? MainWindowPlacement { get; init; }
}
