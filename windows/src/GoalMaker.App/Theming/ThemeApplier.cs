using System.Windows;
using System.Windows.Media;
using Wpf.Ui.Appearance;
using Wpf.Ui.Controls;
using ThemeMode = GoalMaker.Core.Settings.ThemeMode;

namespace GoalMaker.App.Theming;

/// <summary>
/// Applies light, dark or the Windows theme through WPF UI's theme manager, always with GoalMaker's
/// brand accent instead of the Windows accent. Following the Windows theme needs the window's
/// handle, so watching starts once the window has loaded.
/// </summary>
public sealed class ThemeApplier(Color brandAccent)
{
    private const WindowBackdropType Backdrop = WindowBackdropType.Mica;
    private Window? window;
    private bool watching;
    private ThemeMode current = ThemeMode.System;

    public void Attach(Window target)
    {
        window = target;
        target.Loaded += (_, _) => UpdateWatcher();
    }

    public void Apply(ThemeMode mode)
    {
        current = mode;
        var theme = mode switch
        {
            ThemeMode.Light => ApplicationTheme.Light,
            ThemeMode.Dark => ApplicationTheme.Dark,
            _ => SystemIsDark() ? ApplicationTheme.Dark : ApplicationTheme.Light,
        };
        ApplicationThemeManager.Apply(theme, Backdrop, false);
        ApplicationAccentColorManager.Apply(brandAccent, theme, false, false);
        UpdateWatcher();
    }

    private void UpdateWatcher()
    {
        if (window is not { IsLoaded: true })
        {
            return;
        }

        var shouldWatch = current == ThemeMode.System;
        if (shouldWatch && !watching)
        {
            SystemThemeWatcher.Watch(window, Backdrop, false);
        }
        else if (!shouldWatch && watching)
        {
            SystemThemeWatcher.UnWatch(window);
        }

        watching = shouldWatch;
    }

    private static bool SystemIsDark() =>
        ApplicationThemeManager.GetSystemTheme() is SystemTheme.Dark or SystemTheme.Glow or SystemTheme.CapturedMotion
            or SystemTheme.HCBlack;
}
