using System.Windows.Controls;
using System.Windows.Media.Imaging;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using H.NotifyIcon;

namespace GoalMaker.App.Shell;

/// <summary>The tray icon: a left click opens GoalMaker; the menu opens it or quits.</summary>
public sealed class TrayIcon : IDisposable
{
    private readonly TaskbarIcon icon;

    public TrayIcon(IStrings strings, bool isDevBuild, Action open, Action quit)
    {
        var menu = new ContextMenu();
        menu.Items.Add(new MenuItem { Header = strings.Get("Tray.Open"), Command = new RelayCommand(open) });
        menu.Items.Add(new Separator());
        menu.Items.Add(new MenuItem { Header = strings.Get("Tray.Quit"), Command = new RelayCommand(quit) });

        icon = new TaskbarIcon
        {
            ToolTipText = isDevBuild ? strings.Get("App.Name") + " (dev)" : strings.Get("App.Name"),
            IconSource = new BitmapImage(new Uri("pack://application:,,,/Assets/GoalMaker.ico")),
            ContextMenu = menu,
            NoLeftClickDelay = true,
            LeftClickCommand = new RelayCommand(open),
        };
        icon.ForceCreate(enablesEfficiencyMode: false);
    }

    public void Dispose() => icon.Dispose();
}
