using System.ComponentModel;
using System.Windows;
using System.Windows.Threading;
using GoalMaker.App.Composition;
using GoalMaker.App.Startup;
using GoalMaker.App.Views;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.Shell;

/// <summary>
/// The main window. Closing hides it to the tray unless the app is quitting. It reopens where it was
/// last, when that spot is still on a connected screen.
/// </summary>
public partial class MainWindow
{
    private readonly ISettingsStore settings;
    private readonly DispatcherTimer placementSaver;
    private Type pendingPage = typeof(TodayPage);

    public MainWindow(AppGraph graph)
    {
        InitializeComponent();
        settings = graph.Settings;
        DataContext = graph.Shell;
        Navigation.SetPageProviderService(new PageProvider(new Dictionary<Type, Func<object>>
        {
            [typeof(TodayPage)] = () => new TodayPage(graph.Shell),
            [typeof(SettingsPage)] = () => new SettingsPage(graph.SettingsPage),
        }));
        Navigation.Loaded += (_, _) => NavigateWhenReady();
        Navigation.IsVisibleChanged += (_, _) => NavigateWhenReady();

        RestorePlacement();
        placementSaver = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(600) };
        placementSaver.Tick += (_, _) =>
        {
            placementSaver.Stop();
            SavePlacement();
        };
        LocationChanged += (_, _) => placementSaver.Start();
        SizeChanged += (_, _) => placementSaver.Start();
        StateChanged += (_, _) => placementSaver.Start();
    }

    public bool AllowClose { get; set; }

    public void Open(AppPage page)
    {
        pendingPage = page == AppPage.Settings ? typeof(SettingsPage) : typeof(TodayPage);
        NavigateWhenReady();
    }

    protected override void OnClosing(CancelEventArgs e)
    {
        SavePlacement();
        if (!AllowClose)
        {
            e.Cancel = true;
            Hide();
        }

        base.OnClosing(e);
    }

    // NavigationView can only navigate once its template is applied: after Loaded, while visible.
    private void NavigateWhenReady()
    {
        if (Navigation.IsLoaded && Navigation.IsVisible)
        {
            Dispatcher.BeginInvoke(DispatcherPriority.Loaded, () => Navigation.Navigate(pendingPage));
        }
    }

    private void RestorePlacement()
    {
        var placement = settings.MainWindowPlacement;
        if (placement is null || !placement.FitsWithin(
                SystemParameters.VirtualScreenLeft,
                SystemParameters.VirtualScreenTop,
                SystemParameters.VirtualScreenWidth,
                SystemParameters.VirtualScreenHeight))
        {
            return;
        }

        WindowStartupLocation = WindowStartupLocation.Manual;
        Left = placement.Left;
        Top = placement.Top;
        Width = placement.Width;
        Height = placement.Height;
        if (placement.Maximized)
        {
            WindowState = WindowState.Maximized;
        }
    }

    private void SavePlacement()
    {
        if (!IsLoaded || WindowState == WindowState.Minimized)
        {
            return;
        }

        var bounds = WindowState == WindowState.Normal ? new Rect(Left, Top, ActualWidth, ActualHeight) : RestoreBounds;
        if (bounds.IsEmpty)
        {
            return;
        }

        settings.MainWindowPlacement = new WindowPlacement(
            bounds.Left,
            bounds.Top,
            bounds.Width,
            bounds.Height,
            WindowState == WindowState.Maximized);
    }
}
