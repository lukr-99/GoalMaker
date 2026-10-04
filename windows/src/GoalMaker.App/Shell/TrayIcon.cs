using System.IO;
using System.Security.Cryptography;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using CommunityToolkit.Mvvm.Input;
using H.NotifyIcon;

namespace GoalMaker.App.Shell;

/// <summary>
/// The tray icon (spec, stories 79 and 80): a left click shows the Today flyout, a double click
/// opens GoalMaker, and the right click opens <see cref="TrayMenu"/>.
/// <para>
/// The keyboard gets the same (M6-05): with the icon chosen in the notification area (Win+B, then the
/// arrows), Enter or Space shows the flyout and Shift+F10 or the menu key opens the menu. The flyout
/// takes the keyboard on its first control, Tab cycles inside it, and Esc closes it and hands the
/// keyboard back to the notification area.
/// </para>
/// <para>
/// The tray kit's <c>TrayIconHost</c> runs one action on a left click and has no flyout and no double
/// click, so this stays GoalMaker's own, on the same H.NotifyIcon the kit brings. It takes its menu
/// from the kit's menu builder and sets its icon from .ico bytes the way the kit's host does.
/// </para>
/// </summary>
public sealed class TrayIcon : IDisposable
{
    private readonly TaskbarIcon icon;
    private readonly Dictionary<string, System.Drawing.Icon> icons = [];

    /// <param name="toolTip">The tooltip.</param>
    /// <param name="flyout">What a left click shows.</param>
    /// <param name="flyoutOpening">Runs just before the flyout opens, to refresh it.</param>
    /// <param name="open">What a double click does.</param>
    /// <param name="menu">The right-click menu.</param>
    public TrayIcon(string toolTip, UIElement flyout, Action flyoutOpening, Action open, ContextMenu menu)
    {
        icon = new TaskbarIcon
        {
            ToolTipText = toolTip,
            IconSource = new BitmapImage(new Uri("pack://application:,,,/Assets/GoalMaker.ico")),
            ContextMenu = menu,
            TrayPopup = flyout,
            NoLeftClickDelay = true,
            DoubleClickCommand = new RelayCommand(() =>
            {
                CloseFlyout();
                open();
            }),
        };
        icon.TrayPopupOpen += (_, _) => flyoutOpening();
        // H.NotifyIcon only raises the keyboard's choices; it opens nothing for them on its own.
        icon.TrayKeyboardKeySelect += (_, _) => icon.ShowTrayPopup(TaskbarIcon.GetPopupTrayPosition());
        icon.TrayKeyboardContextMenu += (_, _) => OpenMenuFromKeyboard();
        TaskbarIcon.AddPopupOpenedHandler(flyout, (_, _) => flyout.MoveFocus(new TraversalRequest(FocusNavigationDirection.First)));
        flyout.KeyDown += (_, e) =>
        {
            if (e.Key == Key.Escape)
            {
                e.Handled = true;
                CloseFlyout();
                ReturnFocus();
            }
        };
        icon.ForceCreate(enablesEfficiencyMode: false);
    }

    /// <summary>
    /// Shows the logo in the current theme's colors from a whole .ico file's bytes. Icons are cached by
    /// content, so switching between themes makes each only once.
    /// </summary>
    public void SetIcon(byte[]? iconFile)
    {
        if (iconFile is null)
        {
            return;
        }

        var hash = Convert.ToHexString(SHA256.HashData(iconFile));
        if (!icons.TryGetValue(hash, out var image))
        {
            using var stream = new MemoryStream(iconFile);
            image = new System.Drawing.Icon(stream);
            icons[hash] = image;
        }

        icon.Icon = image;
    }

    /// <summary>Closes the flyout, for example after the quick-add box took over.</summary>
    public void CloseFlyout() => icon.CloseTrayPopup();

    // A right click also ends in the message the menu key sends, after H.NotifyIcon has opened the
    // menu at the pointer; only a menu that is not open yet opens here, by the tray.
    private void OpenMenuFromKeyboard() => icon.Dispatcher.BeginInvoke(DispatcherPriority.Background, () =>
    {
        if (icon.ContextMenu is { IsOpen: false })
        {
            icon.ShowContextMenu(TaskbarIcon.GetPopupTrayPosition());
        }
    });

    // Back to the icon in the notification area, as the shell asks of an icon whose UI is done.
    private void ReturnFocus()
    {
        try
        {
            icon.TrayIcon.SetFocus();
        }
        catch (InvalidOperationException)
        {
            // The shell would not take it (no taskbar); the keyboard stays where Windows puts it.
        }
    }

    public void Dispose()
    {
        icon.Dispose();
        foreach (var image in icons.Values)
        {
            image.Dispose();
        }

        icons.Clear();
    }
}
