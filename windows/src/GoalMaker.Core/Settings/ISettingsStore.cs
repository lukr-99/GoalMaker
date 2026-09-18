using GoalMaker.Core.Backend;

namespace GoalMaker.Core.Settings;

/// <summary>Device-local settings that are not synced: the appearance and, in dev builds, a backend override.</summary>
public interface ISettingsStore
{
    Appearance Appearance { get; set; }

    /// <summary>Dev builds only: another Supabase project to use from the next app start.</summary>
    BackendEnvironment? BackendOverride { get; set; }

    /// <summary>The main window's last position, restored at start-up when it still fits a screen.</summary>
    WindowPlacement? MainWindowPlacement { get; set; }
}
