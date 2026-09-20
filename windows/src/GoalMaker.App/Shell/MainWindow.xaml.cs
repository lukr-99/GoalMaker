using System.ComponentModel;
using System.Windows;
using System.Windows.Media.Animation;
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
        Navigation.SetPageProviderService(new PageProvider(new Dictionary<Type, Func<object>>
        {
            [typeof(TodayPage)] = () => new TodayPage(graph.Today),
            [typeof(TomorrowPage)] = () => new TomorrowPage(graph.Tomorrow),
            [typeof(InboxPage)] = () => new InboxPage(graph.Inbox),
            [typeof(PlanPage)] = () => new PlanPage(graph.Plan),
            [typeof(SettingsPage)] = () => new SettingsPage(graph.SettingsPage, graph.Connector),
            [typeof(ActivityPage)] = () => new ActivityPage(graph.Activity),
            [typeof(GoalsPage)] = () => new GoalsPage(graph.GoalsPage),
            [typeof(HabitsPage)] = () => new HabitsPage(graph.HabitsPage),
            [typeof(ReviewsPage)] = () => new ReviewsPage(graph.ReviewsPage),
            [typeof(StatsPage)] = () => new StatsPage(graph.StatsPage),
            [typeof(ProjectsPage)] = () => new ProjectsPage(graph.ProjectsPage),
            [typeof(CalendarPage)] = () => new CalendarPage(graph.CalendarPage),
            [typeof(ReviewPage)] = () => new ReviewPage(graph.Review),
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

        // The launch moment, the first time the window shows (the owner's idea, 2026-09-19).
        IntroLogo.PrepareIntro();
        ContentRendered += (_, _) => PlayIntro(graph.Theme.MotionReduced);
    }

    public bool AllowClose { get; set; }

    // The logo draws its arrow (two emphasized beats of 400 ms), holds a moment, and the app fades in
    // under it. Reduce motion skips it; a click skips it too.
    private void PlayIntro(bool reduced)
    {
        if (reduced || Intro.Visibility != Visibility.Visible)
        {
            Intro.Visibility = Visibility.Collapsed;
            return;
        }

        IntroLogo.PlayIntro();
        var fade = new DoubleAnimation(1, 0, TimeSpan.FromMilliseconds(250)) { BeginTime = TimeSpan.FromMilliseconds(1050) };
        fade.Completed += (_, _) => Intro.Visibility = Visibility.Collapsed;
        Intro.MouseDown += (_, _) => Intro.Visibility = Visibility.Collapsed;
        Intro.BeginAnimation(OpacityProperty, fade);
    }

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
            AppPage.Activity => typeof(ActivityPage),
            AppPage.Goals => typeof(GoalsPage),
            AppPage.Habits => typeof(HabitsPage),
            AppPage.Reviews => typeof(ReviewsPage),
            AppPage.Stats => typeof(StatsPage),
            AppPage.Projects => typeof(ProjectsPage),
            AppPage.Calendar => typeof(CalendarPage),
            AppPage.Review => typeof(ReviewPage),
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
