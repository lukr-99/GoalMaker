using System.Runtime.ExceptionServices;
using System.Windows;
using System.Windows.Threading;
using GoalMaker.App.Theming;
using GoalMaker.Core.Settings;
using GoalMaker.Infrastructure.Sync;

namespace GoalMaker.App.Tests;

/// <summary>
/// One WPF application for the tests that need the app's resources the way the app has them (a
/// template's StaticResource looks in the application, not in the element tree), in the default
/// theme, on one UI thread
/// that stays up for the whole run. Never the App class, which would start GoalMaker. Its tests are
/// in <see cref="WpfCollection"/>, so they run on their own, after the rest.
/// </summary>
internal static class WpfApp
{
    private static readonly Lazy<Dispatcher> Ui = new(Start, LazyThreadSafetyMode.ExecutionAndPublication);

    /// <summary>Runs <paramref name="test"/> on the UI thread and rethrows what failed there.</summary>
    public static void Run(Action test)
    {
        ExceptionDispatchInfo? failure = null;
        Ui.Value.Invoke(() =>
        {
            try
            {
                test();
            }
            catch (Exception exception)
            {
                failure = ExceptionDispatchInfo.Capture(exception);
            }
        });
        failure?.Throw();
    }

    private static Dispatcher Start()
    {
        Dispatcher? dispatcher = null;
        using var ready = new ManualResetEventSlim();
        var thread = new Thread(() =>
        {
            var app = new Application { ShutdownMode = ShutdownMode.OnExplicitShutdown };
            // The same dictionaries in the same order as App.xaml.cs, and the default theme over them,
            // so sizes, paddings and fonts are the app's own.
            AppResources.Merge(app.Resources);
            new ThemeApplier(ContractResources.Themes(), app.Resources, ContractResources.Logo()).Apply(Appearance.Default);
            dispatcher = Dispatcher.CurrentDispatcher;
            ready.Set();
            Dispatcher.Run();
        })
        {
            IsBackground = true,
            Name = "WPF tests",
        };
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
        ready.Wait();
        return dispatcher!;
    }
}
