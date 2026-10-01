using System.IO;
using System.Windows;
using DotNetLib.Tray;
using GoalMaker.App.Diagnostics;
using GoalMaker.App.Localization;
using GoalMaker.App.Theming;
using GoalMaker.Infrastructure.Sync;

namespace GoalMaker.App.Shell;

/// <summary>
/// When GoalMaker cannot start at all, usually because its data file will not open, the owner gets
/// a plain message and the place to look, rather than a window that never appears (M6-06). The
/// exception goes to logs/crash.log like any other. The message is the tray kit's
/// <see cref="TrayMessageWindow"/> in the default theme's colors, light or dark as Windows is, since
/// the saved settings may be what failed.
/// </summary>
public static class StartupFailure
{
    public static void Show(Exception error, IStrings strings, string dataFolder, ResourceDictionary appResources)
    {
        CrashLog.Write(Path.Combine(dataFolder, "logs", "crash.log"), error);
        using var theme = DefaultTheme(appResources);
        theme.Apply(TrayThemeMode.System);
        TrayMessageWindow.Inform(strings.Get("App.Name"), strings.Get("Startup.Failed", dataFolder), theme.Attach);
    }

    // The default theme's palettes, or the kit's neutral ones if even the themes will not load.
    private static TrayThemeApplier DefaultTheme(ResourceDictionary appResources)
    {
        try
        {
            var (light, dark) = TrayPalettes.For(ContractResources.Themes().Theme(null), pureBlack: false);
            return new TrayThemeApplier(appResources, TrayThemeApplier.WindowsAppsUseDark, light, dark);
        }
        catch (Exception)
        {
            return new TrayThemeApplier(appResources, TrayThemeApplier.WindowsAppsUseDark);
        }
    }
}
