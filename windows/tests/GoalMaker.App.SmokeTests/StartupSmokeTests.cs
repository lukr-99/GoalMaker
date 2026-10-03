using System.IO;
using System.Windows;
using System.Windows.Threading;
using GoalMaker.App.Composition;
using GoalMaker.App.Localization;
using GoalMaker.App.Shell;
using GoalMaker.App.Startup;
using GoalMaker.App.Theming;
using GoalMaker.Infrastructure.Storage;
using Wpf.Ui.Controls;

namespace GoalMaker.App.SmokeTests;

/// <summary>
/// GoalMaker starts the way <see cref="App"/> starts a dev build, over a throwaway data folder: the
/// resources merge, the composition root builds, the shell puts up the tray icon, the quick-add box and
/// the main window (shown with --no-activate, so it never takes the focus), and every page opens in it.
/// Anything that throws on the way fails the test. It shows a window and a tray icon for a few seconds,
/// so it lives in its own project and process (docs/setup/ci.md).
/// </summary>
[Trait("Category", "Smoke")]
public sealed class StartupSmokeTests
{
    private static readonly TimeSpan Timeout = TimeSpan.FromSeconds(30);

    [Fact]
    public void ADevBuildStartsAndOpensEveryPage() => OnUiThread(folder =>
    {
        var app = new Application { ShutdownMode = ShutdownMode.OnExplicitShutdown };
        // The same dictionaries in the same order as App.xaml.cs.
        AppResources.Merge(app.Resources);
        var strings = new ResourceStrings(app);
        var build = BuildConfiguration.FromAssembly(typeof(App).Assembly);
        Assert.True(build.IsDevBuild, "The smoke test starts a dev build, which keeps its rows on this PC.");

        void RunOnUi(Action action)
        {
            if (app.Dispatcher.CheckAccess())
            {
                action();
            }
            else
            {
                app.Dispatcher.Invoke(action);
            }
        }

        using var graph = new AppGraph(build, strings, app.Resources, RunOnUi, () => { }, () => { }, paths: new AppDataPaths(folder));
        using var shell = new AppShell(graph, build, strings, RunOnUi, () => { });
        var navigation = (NavigationView)shell.Window.FindName("Navigation");
        var shown = new List<Type>();
        navigation.Navigated += (_, e) =>
        {
            if (e.Page is { } page)
            {
                shown.Add(page.GetType());
            }
        };

        shell.Handle(StartupOptions.Parse(["--no-activate"]), secondLaunch: false);
        Assert.True(shell.Window.IsVisible);

        // As App does next: the session comes back, which for a dev build is the device's own, and
        // only then does the window show the app rather than signing in.
        var session = RestoreSessionAsync(graph);
        PumpUntil(() => session.IsCompleted, "session");
        session.GetAwaiter().GetResult();
        PumpUntil(() => shown.Contains(typeof(Views.TodayPage)), "Today page");

        foreach (var page in Enum.GetValues<AppPage>().Where(page => page != AppPage.Today))
        {
            shown.Clear();
            shell.Window.Open(page);
            PumpUntil(() => shown.Any(type => type.Name == page + "Page"), page + " page");
        }
    });

    private static async Task RestoreSessionAsync(AppGraph graph)
    {
        await graph.Auth.InitializeAsync(CancellationToken.None);
        await graph.SignInWatch.EnforceAsync();
    }

    // Runs the dispatcher until the condition holds. Whatever a page throws while it loads comes out of
    // PushFrame here and fails the test.
    private static void PumpUntil(Func<bool> done, string what)
    {
        var deadline = DateTime.UtcNow + Timeout;
        while (!done())
        {
            if (DateTime.UtcNow > deadline)
            {
                throw new TimeoutException($"Waited {Timeout.TotalSeconds} s for the {what}");
            }

            var frame = new DispatcherFrame();
            Dispatcher.CurrentDispatcher.BeginInvoke(DispatcherPriority.Background, () => frame.Continue = false);
            Dispatcher.PushFrame(frame);
            Thread.Sleep(10);
        }
    }

    // WPF needs an STA thread; the data folder is a fresh one under the temp folder, removed afterwards.
    private static void OnUiThread(Action<string> test)
    {
        var folder = Path.Combine(Path.GetTempPath(), "goalmaker-smoke-" + Guid.NewGuid().ToString("N"));
        Exception? failure = null;
        var thread = new Thread(() =>
        {
            try
            {
                test(folder);
            }
            catch (Exception exception)
            {
                failure = exception;
            }
            finally
            {
                // As Quit's Shutdown does: the thread's windows close before it ends, or Windows calls
                // into a thread that is gone and the process dies.
                Dispatcher.CurrentDispatcher.InvokeShutdown();
            }
        });
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
        thread.Join();
        try
        {
            Microsoft.Data.Sqlite.SqliteConnection.ClearAllPools();
            Directory.Delete(folder, recursive: true);
        }
        catch (IOException)
        {
            // A file still held a moment after the test; the temp folder is cleaned up eventually.
        }

        if (failure is not null)
        {
            throw new InvalidOperationException("Start-up failed", failure);
        }
    }
}
