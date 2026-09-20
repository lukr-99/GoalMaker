using System.Windows;
using System.Windows.Controls;
using System.Windows.Media.Imaging;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.App.Startup;
using H.NotifyIcon;

namespace GoalMaker.App.Shell;

/// <summary>
/// The tray icon (spec, stories 79, 80 and 11): a left click shows the Today flyout, a double click
/// opens GoalMaker, and the menu opens it, opens a mini window, adds a task through the quick-add
/// box, or quits.
/// </summary>
public sealed class TrayIcon : IDisposable
{
    private readonly TaskbarIcon icon;

    public TrayIcon(
        IStrings strings,
        bool isDevBuild,
        UIElement flyout,
        Action flyoutOpening,
        Action open,
        Action quickAdd,
        Action<MiniPage> mini,
        Action quit)
    {
        void Open()
        {
            icon?.CloseTrayPopup();
            open();
        }

        void QuickAdd()
        {
            icon?.CloseTrayPopup();
            quickAdd();
        }

        void Mini(MiniPage page)
        {
            icon?.CloseTrayPopup();
            mini(page);
        }

        var menu = new ContextMenu();
        menu.Items.Add(new MenuItem { Header = strings.Get("Tray.Open"), Command = new RelayCommand(Open) });
        menu.Items.Add(new MenuItem { Header = strings.Get("Tray.QuickAdd"), Command = new RelayCommand(QuickAdd) });
        menu.Items.Add(new Separator());
        menu.Items.Add(new MenuItem { Header = strings.Get("Tray.MiniToday"), Command = new RelayCommand(() => Mini(MiniPage.Today)) });
        menu.Items.Add(new MenuItem { Header = strings.Get("Tray.MiniHabits"), Command = new RelayCommand(() => Mini(MiniPage.Habits)) });
        menu.Items.Add(new Separator());
        menu.Items.Add(new MenuItem { Header = strings.Get("Tray.Quit"), Command = new RelayCommand(quit) });

        icon = new TaskbarIcon
        {
            ToolTipText = isDevBuild ? strings.Get("App.Name") + " (dev)" : strings.Get("App.Name"),
            IconSource = new BitmapImage(new Uri("pack://application:,,,/Assets/GoalMaker.ico")),
            ContextMenu = menu,
            TrayPopup = flyout,
            NoLeftClickDelay = true,
            DoubleClickCommand = new RelayCommand(Open),
        };
        icon.TrayPopupOpen += (_, _) => flyoutOpening();
        icon.ForceCreate(enablesEfficiencyMode: false);
    }

    /// <summary>
    /// Shows the logo in the current theme's colors. The tray reads icons from files only, so the bytes
    /// land in <paramref name="folder"/>, one file per set of colors.
    /// </summary>
    public void SetIcon(byte[]? iconFile, string folder)
    {
        if (iconFile is null)
        {
            return;
        }

        var path = System.IO.Path.Combine(folder, $"tray-{Convert.ToHexStringLower(System.Security.Cryptography.SHA256.HashData(iconFile))[..12]}.ico");
        if (!System.IO.File.Exists(path))
        {
            System.IO.File.WriteAllBytes(path, iconFile);
        }

        icon.IconSource = new BitmapImage(new Uri(path));
    }

    /// <summary>Closes the flyout, for example after the quick-add box took over.</summary>
    public void CloseFlyout() => icon.CloseTrayPopup();

    public void Dispose() => icon.Dispose();
}
