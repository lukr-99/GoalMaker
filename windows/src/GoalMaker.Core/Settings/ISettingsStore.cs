using GoalMaker.Core.Backend;

namespace GoalMaker.Core.Settings;

/// <summary>
/// Device-local settings that are not synced: the appearance, when the planning day starts, the
/// sidebar and, in dev builds, a backend override.
/// </summary>
public interface ISettingsStore
{
    Appearance Appearance { get; set; }

    /// <summary>The hour the planning day starts (docs/lists.md), 0 to 6; 4 unless changed.</summary>
    int DayStartHour { get; set; }

    /// <summary>Whether the sidebar is collapsed to icons.</summary>
    bool NavigationCollapsed { get; set; }

    /// <summary>Dev builds only: another Supabase project to use from the next app start.</summary>
    BackendEnvironment? BackendOverride { get; set; }

    /// <summary>The main window's last position, restored at start-up when it still fits a screen.</summary>
    WindowPlacement? MainWindowPlacement { get; set; }
}
