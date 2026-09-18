using System.Diagnostics;
using System.Windows;
using GoalMaker.App.Composition;
using GoalMaker.App.Diagnostics;
using GoalMaker.App.Localization;
using GoalMaker.App.Shell;
using GoalMaker.App.Startup;

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
        graph = new AppGraph(build, strings, Resources, RunOnUi, () => RunOnUi(Quit), () => RunOnUi(Restart));
        CrashLog.Install(this, graph.Paths.Root);
        window = new MainWindow(graph);
        graph.Theme.Attach(window);
        graph.Theme.Apply(graph.Settings.Appearance);
        quickAdd = new QuickAddWindow(graph.QuickAdd, strings);
        tray = new TrayIcon(strings, build.IsDevBuild, new TrayFlyout(graph.TrayFlyout), graph.TrayFlyout.Refresh, ShowMainWindow, SummonQuickAdd, Quit);
        graph.WindowRequested += (_, page) =>
        {
            tray.CloseFlyout();
            ShowMainWindow();
            window.Open(page);
        };
        graph.QuickAddRequested += (_, _) => SummonQuickAdd();

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

        window.ShowActivated = activate;
        window.Show();
        if (window.WindowState == WindowState.Minimized)
        {
            window.WindowState = WindowState.Normal;
        }

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
