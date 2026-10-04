using System.Windows;
using GoalMaker.App.Composition;
using GoalMaker.App.Localization;
using GoalMaker.App.Startup;

namespace GoalMaker.App.Shell;

/// <summary>
/// What the owner sees of a running GoalMaker, built over the composition root: the main window, the
/// tray icon with its Today flyout and menu, the quick-add box and its global shortcut, and the mini
/// windows. <see cref="App"/> builds it once the instance lock and the graph are in place, and the
/// start-up smoke test builds it the same way (docs/setup/local-development.md).
/// </summary>
public sealed class AppShell : IDisposable
{
    private readonly AppGraph graph;
    private readonly IStrings strings;
    private readonly TrayIcon tray;
    private readonly QuickAddWindow quickAdd;
    private readonly QuickAddHotkey hotkey;
    private readonly Dictionary<MiniPage, MiniWindow> miniWindows = [];

    /// <param name="graph">The composition root.</param>
    /// <param name="build">What this build was compiled with; a dev build says so in the tray.</param>
    /// <param name="strings">The copy.</param>
    /// <param name="runOnUi">Runs an action on the UI thread; the shortcut is heard off it.</param>
    /// <param name="quit">What the tray menu's Quit does.</param>
    public AppShell(AppGraph graph, BuildConfiguration build, IStrings strings, Action<Action> runOnUi, Action quit)
    {
        this.graph = graph;
        this.strings = strings;
        Window = new MainWindow(graph);
        graph.Theme.Attach(Window);
        graph.Theme.Apply(graph.Settings.Appearance);
        quickAdd = new QuickAddWindow(graph.QuickAdd, strings, graph.TextScale);
        var menu = TrayMenu.Build(
            strings,
            () => FromTray(ShowMainWindow),
            SummonQuickAdd,
            page => FromTray(() => ShowMini(page)),
            quit);
        var flyout = new TrayFlyout(graph.TrayFlyout);
        graph.TextScale.Follow(flyout);
        tray = new TrayIcon(
            build.IsDevBuild ? strings.Get("App.Name") + " (dev)" : strings.Get("App.Name"),
            flyout,
            graph.TrayFlyout.Refresh,
            ShowMainWindow,
            menu);
        // The tray shows the logo in the theme's colors, like the window and the taskbar (GM.LogoIcon).
        tray.SetIcon(graph.Theme.LogoIconFile);
        graph.Theme.Applied += (_, _) => tray.SetIcon(graph.Theme.LogoIconFile);
        graph.WindowRequested += (_, page) =>
        {
            tray.CloseFlyout();
            ShowMainWindow();
            Window.Open(page);
        };
        graph.QuickAddRequested += (_, _) => SummonQuickAdd();
        graph.MiniRequested += (_, page) => ShowMini(page);

        // The global quick-add shortcut (spec, story 11); Settings shows it and can change it.
        hotkey = new QuickAddHotkey(() => runOnUi(SummonQuickAdd));
        graph.ApplyQuickAddHotkey = hotkey.Apply;
        graph.SettingsPage.ApplyStoredQuickAddHotkey();
    }

    public MainWindow Window { get; }

    /// <summary>
    /// Does what a launch asks for: a mini window, the tray only, or the main window on a page. A first
    /// launch without a page opens Today; a second one only brings the window forward.
    /// </summary>
    public void Handle(StartupOptions options, bool secondLaunch)
    {
        if (options.Mini is { } mini)
        {
            ShowMini(mini);
        }

        if (options.StartInTray)
        {
            return;
        }

        ShowMainWindow(activate: !options.NoActivate);
        if (options.OpenPage is { } page)
        {
            Window.Open(page);
        }
        else if (!secondLaunch)
        {
            Window.Open(AppPage.Today);
        }
    }

    /// <summary>Closes every window for good and takes the tray icon and the shortcut down.</summary>
    public void Dispose()
    {
        Window.AllowClose = true;
        Window.Close();
        foreach (var mini in miniWindows.Values.ToList())
        {
            mini.Close();
        }

        hotkey.Dispose();
        quickAdd.CloseForGood();
        tray.Dispose();
    }

    private void ShowMainWindow() => ShowMainWindow(activate: true);

    // A menu item closes the Today flyout before it opens anything over it.
    private void FromTray(Action action)
    {
        tray.CloseFlyout();
        action();
    }

    /// <summary>
    /// Opens a mini window, or brings the one already there to the front (spec, story 80). Each kind
    /// has one window; closing it puts it away until it is asked for again.
    /// </summary>
    private void ShowMini(MiniPage page)
    {
        if (!miniWindows.TryGetValue(page, out var mini))
        {
            var content = MiniWindowContent.For(page, graph.Today, graph.HabitsPage);
            mini = new MiniWindow(content, strings, graph.Settings, graph.TextScale, ShowMainWindow);
            mini.Closed += (_, _) => miniWindows.Remove(page);
            miniWindows[page] = mini;
        }

        mini.Present();
    }

    private void SummonQuickAdd()
    {
        tray.CloseFlyout();
        quickAdd.Summon();
    }

    private void ShowMainWindow(bool activate)
    {
        // WPF refuses to show a maximized window without activating it, so a window left maximized
        // and asked for with --no-activate goes up normal and is maximized once it is on screen.
        var (show, after) = WindowShow.Plan(activate, Window.WindowState);
        Window.WindowState = show;
        Window.ShowActivated = activate;
        Window.Show();
        Window.WindowState = after;

        if (activate)
        {
            Window.Activate();
        }
    }
}
