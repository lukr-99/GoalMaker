using System.ComponentModel;
using System.Windows;
using System.Windows.Threading;
using GoalMaker.App.Composition;
using GoalMaker.App.Startup;
using GoalMaker.App.ViewModels;
using GoalMaker.App.Views;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.Shell;

/// <summary>
/// The main window. Closing hides it to the tray unless the app is quitting. It reopens where it was
/// last, when that spot is still on a connected screen, with the sidebar as it was left.
/// </summary>
public partial class MainWindow
{
    private readonly ISettingsStore settings;
    private readonly PlanViewModel plan;
    private readonly DispatcherTimer placementSaver;
    private Type pendingPage = typeof(TodayPage);

    public MainWindow(AppGraph graph)
    {
        InitializeComponent();
        settings = graph.Settings;
        plan = graph.Plan;
        graph.PageRequested += (_, page) => Open(page);
        DataContext = graph.Shell;
        FilterPane.DataContext = graph.SidebarFilters;
        Navigation.SetPageProviderService(new PageProvider(new Dictionary<Type, Func<object>>
        {
            [typeof(TodayPage)] = () => new TodayPage(graph.Today),
            [typeof(TomorrowPage)] = () => new TomorrowPage(graph.Tomorrow),
            [typeof(InboxPage)] = () => new InboxPage(graph.Inbox),
            [typeof(PlanPage)] = () => new PlanPage(graph.Plan),
            [typeof(SettingsPage)] = () => new SettingsPage(graph.SettingsPage),
            [typeof(AreasPage)] = () => new AreasPage(graph.AreasPage),
            [typeof(ArchivePage)] = () => new ArchivePage(graph.Archive),
            [typeof(TaskPage)] = () => new TaskPage(graph.TaskDetail),
        }));
        Navigation.IsPaneOpen = !settings.NavigationCollapsed;
        Navigation.PaneOpened += (_, _) => settings.NavigationCollapsed = false;
        Navigation.PaneClosed += (_, _) => settings.NavigationCollapsed = true;
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
        if (page == AppPage.Plan)
        {
            plan.Start();
        }

        pendingPage = page switch
        {
            AppPage.Plan => typeof(PlanPage),
            AppPage.Tomorrow => typeof(TomorrowPage),
            AppPage.Inbox => typeof(InboxPage),
            AppPage.Settings => typeof(SettingsPage),
            AppPage.Areas => typeof(AreasPage),
            AppPage.Archive => typeof(ArchivePage),
            AppPage.Task => typeof(TaskPage),
            _ => typeof(TodayPage),
        };
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
