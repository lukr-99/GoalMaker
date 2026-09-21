using System.Diagnostics;
using System.Windows;
using GoalMaker.App.Composition;
using GoalMaker.App.Diagnostics;
using GoalMaker.App.Localization;
using GoalMaker.App.Shell;
using GoalMaker.App.Startup;
using GoalMaker.Infrastructure.Storage;

namespace GoalMaker.App;

/// <summary>
/// Process lifetime: one instance per user and build kind, the composition root, the tray icon and
/// the main window. Closing the window hides it; only Quit ends the app.
/// </summary>
public partial class App : Application
{
    private SingleInstance? instance;
    private AppGraph? graph;
    private TrayIcon? tray;
    private MainWindow? window;
    private QuickAddWindow? quickAdd;
    private QuickAddHotkey? hotkey;
    private readonly Dictionary<MiniPage, MiniWindow> miniWindows = [];

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        var build = BuildConfiguration.FromAssembly(typeof(App).Assembly);
        instance = SingleInstance.TryAcquire(build.InstanceName);
        if (instance is null)
        {
            SingleInstance.Forward(build.InstanceName, e.Args);
            Shutdown();
            return;
        }

        var strings = new ResourceStrings(this);
        try
        {
            graph = new AppGraph(build, strings, Resources, RunOnUi, () => RunOnUi(Quit), () => RunOnUi(Restart));
        }
        catch (Exception error)
        {
            // Usually the replica: a file that will not open leaves the owner with a message and a
            // place to look, rather than a window that never appears (M6-06).
            StartupFailure.Show(error, strings, new AppDataPaths(build.IsDevBuild).Root);
            Shutdown();
            return;
        }

        CrashLog.Install(this, graph.Paths.Root);
        window = new MainWindow(graph);
        graph.Theme.Attach(window);
        graph.Theme.Apply(graph.Settings.Appearance);
        quickAdd = new QuickAddWindow(graph.QuickAdd, strings);
        tray = new TrayIcon(
            strings, build.IsDevBuild, new TrayFlyout(graph.TrayFlyout), graph.TrayFlyout.Refresh, ShowMainWindow, SummonQuickAdd, ShowMini, Quit);
        // The tray shows the logo in the theme's colors, like the window and the taskbar (GM.LogoIcon).
        tray.SetIcon(graph.Theme.LogoIconFile, graph.Paths.Root);
        graph.Theme.Applied += (_, _) => tray.SetIcon(graph.Theme.LogoIconFile, graph.Paths.Root);
        graph.WindowRequested += (_, page) =>
        {
            tray.CloseFlyout();
            ShowMainWindow();
            window.Open(page);
        };
        graph.QuickAddRequested += (_, _) => SummonQuickAdd();
        graph.MiniRequested += (_, page) => ShowMini(page);

        // The global quick-add shortcut (spec, story 11); Settings shows it and can change it.
        hotkey = new QuickAddHotkey(() => RunOnUi(SummonQuickAdd));
        graph.ApplyQuickAddHotkey = hotkey.Apply;
        graph.SettingsPage.ApplyStoredQuickAddHotkey();
        instance.Listen(arguments => RunOnUi(() => Handle(StartupOptions.Parse(arguments), secondLaunch: true)));

        Handle(StartupOptions.Parse(e.Args), secondLaunch: false);
        _ = graph.Auth.InitializeAsync(CancellationToken.None);
    }

    private void Handle(StartupOptions options, bool secondLaunch)
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
            window?.Open(page);
        }
        else if (!secondLaunch)
        {
            window?.Open(AppPage.Today);
        }
    }

    private void ShowMainWindow() => ShowMainWindow(activate: true);

    /// <summary>
    /// Opens a mini window, or brings the one already there to the front (spec, story 80). Each kind
    /// has one window; closing it puts it away until it is asked for again.
    /// </summary>
    private void ShowMini(MiniPage page)
    {
        if (graph is null)
        {
            return;
        }

        if (!miniWindows.TryGetValue(page, out var mini))
        {
            var content = MiniWindowContent.For(page, graph.Today, graph.HabitsPage);
            mini = new MiniWindow(content, new ResourceStrings(this), graph.Settings, ShowMainWindow);
            mini.Closed += (_, _) => miniWindows.Remove(page);
            miniWindows[page] = mini;
        }

        mini.Show();
        if (mini.WindowState == WindowState.Minimized)
        {
            mini.WindowState = WindowState.Normal;
        }

        mini.Activate();
    }

    private void SummonQuickAdd()
    {
        tray?.CloseFlyout();
        quickAdd?.Summon();
    }

    private void ShowMainWindow(bool activate)
    {
        if (window is null)
        {
            return;
        }

        // WPF refuses to show a maximized window without activating it, so a window left maximized
        // and asked for with --no-activate goes up normal and is maximized once it is on screen.
        var (show, after) = WindowShow.Plan(activate, window.WindowState);
        window.WindowState = show;
        window.ShowActivated = activate;
        window.Show();
        window.WindowState = after;

        if (activate)
        {
            window.Activate();
        }
    }

    private void Quit()
    {
        if (window is not null)
        {
            window.AllowClose = true;
            window.Close();
        }

        foreach (var mini in miniWindows.Values.ToList())
        {
            mini.Close();
        }

        hotkey?.Dispose();
        quickAdd?.CloseForGood();
        tray?.Dispose();
        graph?.Dispose();
        instance?.Dispose();
        instance = null;
        Shutdown();
    }

    private void Restart()
    {
        var executable = Environment.ProcessPath;
        instance?.Dispose();
        instance = null;
        if (executable is not null)
        {
            Process.Start(new ProcessStartInfo(executable) { UseShellExecute = false })?.Dispose();
        }

        Quit();
    }

    private void RunOnUi(Action action)
    {
        if (Dispatcher.CheckAccess())
        {
            action();
        }
        else
        {
            Dispatcher.Invoke(action);
        }
    }
}
