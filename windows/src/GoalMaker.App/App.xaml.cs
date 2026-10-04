using System.Diagnostics;
using System.Windows;
using GoalMaker.App.Composition;
using GoalMaker.App.Diagnostics;
using GoalMaker.App.Localization;
using GoalMaker.App.Shell;
using GoalMaker.App.Startup;
using GoalMaker.App.Theming;
using GoalMaker.Infrastructure.Storage;

namespace GoalMaker.App;

/// <summary>
/// Process lifetime: one instance per user and build kind, the composition root, and the shell over
/// it (<see cref="AppShell"/>: the tray icon, the main window and the rest). Closing the window hides
/// it; only Quit ends the app. The instance lock comes first; only then are the resources merged
/// (<see cref="AppResources"/>).
/// </summary>
public partial class App : Application
{
    private SingleInstance? instance;
    private AppGraph? graph;
    private AppShell? shell;

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

        AppResources.Merge(Resources);
        var strings = new ResourceStrings(this);
        var options = StartupOptions.Parse(e.Args);
        try
        {
            graph = new AppGraph(build, strings, Resources, RunOnUi, () => RunOnUi(Quit), () => RunOnUi(Restart), options.SignIn);
        }
        catch (Exception error)
        {
            // Usually the replica: a file that will not open leaves the owner with a message and a
            // place to look, rather than a window that never appears (M6-06).
            StartupFailure.Show(error, strings, new AppDataPaths(build.IsDevBuild).Root, Resources);
            Shutdown();
            return;
        }

        CrashLog.Install(this, graph.Paths.Root);
        var started = new AppShell(graph, build, strings, RunOnUi, Quit);
        shell = started;
        instance.Listen(arguments => RunOnUi(() => started.Handle(StartupOptions.Parse(arguments), secondLaunch: true)));

        started.Handle(options, secondLaunch: false);
        _ = RestoreSessionAsync(graph);
    }

    // The stored session comes back first; a PC whose week has run out is signed out again at once.
    private static async Task RestoreSessionAsync(AppGraph graph)
    {
        await graph.Auth.InitializeAsync(CancellationToken.None);
        await graph.SignInWatch.EnforceAsync();
    }

    private void Quit()
    {
        shell?.Dispose();
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
