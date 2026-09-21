using System.IO;
using System.Windows;
using GoalMaker.App.Localization;
using GoalMaker.App.Diagnostics;

namespace GoalMaker.App.Shell;

/// <summary>
/// When GoalMaker cannot start at all, usually because its data file will not open, the owner gets
/// a plain message and the place to look, rather than a window that never appears (M6-06). The
/// exception goes to logs/crash.log like any other.
/// </summary>
public static class StartupFailure
{
    public static void Show(Exception error, IStrings strings, string dataFolder)
    {
        CrashLog.Write(Path.Combine(dataFolder, "logs", "crash.log"), error);
        MessageBox.Show(
            strings.Get("Startup.Failed", dataFolder),
            strings.Get("App.Name"),
            MessageBoxButton.OK,
            MessageBoxImage.Warning);
    }
}
