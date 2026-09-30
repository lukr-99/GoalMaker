using System.ComponentModel;
using System.Windows;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Threading;
using GoalMaker.App.Composition;
using GoalMaker.App.Startup;
using GoalMaker.App.ViewModels;
using GoalMaker.App.Views;
using GoalMaker.Core.Settings;
using Wpf.Ui.Controls;

namespace GoalMaker.App.Shell;

/// <summary>
/// The main window. Closing hides it to the tray unless the app is quitting. It reopens where it was
/// last, when that spot is still on a connected screen, with the sidebar as it was left. The sidebar
/// is the pinned places and All places, built from this PC's pins, with Go to on Ctrl+K (ADR 0014).
/// </summary>
public partial class MainWindow
{
    private readonly ISettingsStore settings;
    private readonly PlanViewModel plan;
    private readonly DispatcherTimer placementSaver;
    private readonly bool motionReduced;
    private Type pendingPage = typeof(TodayPage);

    public MainWindow(AppGraph graph)
    {
        Places = graph.Places;
        motionReduced = graph.Theme.MotionReduced;
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
            [typeof(SettingsPage)] = () => new SettingsPage(graph.SettingsPage, graph.Connector, graph.ProblemsPage),
            [typeof(ActivityPage)] = () => new ActivityPage(graph.Activity),
            [typeof(GoalsPage)] = () => new GoalsPage(graph.GoalsPage),
            [typeof(HabitsPage)] = () => new HabitsPage(graph.HabitsPage),
            [typeof(ReviewsPage)] = () => new ReviewsPage(graph.ReviewsPage),
            [typeof(StatsPage)] = () => new StatsPage(graph.StatsPage),
            [typeof(WantsPage)] = () => new WantsPage(graph.WantsPage),
            [typeof(TallyPage)] = () => new TallyPage(graph.TallyPage),
            [typeof(ProjectsPage)] = () => new ProjectsPage(graph.ProjectsPage),
            [typeof(CalendarPage)] = () => new CalendarPage(graph.CalendarPage),
            [typeof(ReviewPage)] = () => new ReviewPage(graph.Review),
            [typeof(AreasPage)] = () => new AreasPage(graph.AreasPage),
            [typeof(ArchivePage)] = () => new ArchivePage(graph.Archive),
            [typeof(TaskPage)] = () => new TaskPage(graph.TaskDetail),
        }));
        PlaceSidebar.Build(Navigation.MenuItems, Places, allOpen: true);
        Places.PinsChanged += (_, _) => RebuildSidebar();
        Places.PlaceChosen += (_, place) =>
        {
            pendingPage = PlaceSidebar.PageOf(place);
            NavigateWhenReady();
        };
        Places.PropertyChanged += OnPlacesChanged;
        Navigation.Navigated += (_, e) => Places.Current = e.Page is null ? null : PlaceSidebar.PlaceOf(e.Page.GetType());
        PaletteInput.PreviewKeyDown += OnPaletteKey;
        PaletteScrim.MouseDown += (_, _) => Places.ClosePaletteCommand.Execute(null);
        PaletteList.MouseLeftButtonUp += (_, _) =>
        {
            if (PaletteList.SelectedItem is PlaceEntry entry)
            {
                Places.ChooseCommand.Execute(entry.Id);
            }
        };
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

    /// <summary>The pinned places and Go to, which the sidebar, the title bar's Pin and the Go to box bind to.</summary>
    public PlacesViewModel Places { get; }

    // The pins changed: build the sidebar again, keep All places as it was, and mark the page on show.
    private void RebuildSidebar()
    {
        var allOpen = Navigation.MenuItems.OfType<NavigationViewItem>()
            .FirstOrDefault(item => Equals(item.Tag, PlaceSidebar.AllPlaces))?.IsExpanded ?? true;
        PlaceSidebar.Build(Navigation.MenuItems, Places, allOpen);
        // The page on show stays open, so mark its item in the new sidebar rather than navigating again.
        if (Places.Current is { } place)
        {
            Dispatcher.BeginInvoke(DispatcherPriority.Loaded, () => PlaceSidebar.MarkActive(Navigation.MenuItems, place));
        }
    }

    private void OnPlacesChanged(object? sender, PropertyChangedEventArgs e)
    {
        switch (e.PropertyName)
        {
            case nameof(PlacesViewModel.IsCurrentPinned):
                PinIcon.Symbol = Places.IsCurrentPinned ? SymbolRegular.PinOff24 : SymbolRegular.Pin24;
                break;
            case nameof(PlacesViewModel.IsPaletteOpen) when Places.IsPaletteOpen:
                ShowPalette();
                break;
        }
    }

    // Go to arrives the way a dialog does: a short grow and fade on the emphasized curve, then the
    // text box takes the keyboard. Reduce motion shows it at once.
    private void ShowPalette()
    {
        Dispatcher.BeginInvoke(DispatcherPriority.Input, () =>
        {
            PaletteInput.Focus();
            Keyboard.Focus(PaletteInput);
        });
        if (motionReduced)
        {
            return;
        }

        var ease = new CubicEase { EasingMode = EasingMode.EaseOut };
        var duration = TimeSpan.FromMilliseconds(250);
        var scale = (ScaleTransform)PaletteBox.RenderTransform;
        scale.BeginAnimation(ScaleTransform.ScaleXProperty, new DoubleAnimation(0.96, 1, duration) { EasingFunction = ease });
        scale.BeginAnimation(ScaleTransform.ScaleYProperty, new DoubleAnimation(0.96, 1, duration) { EasingFunction = ease });
        PaletteBox.BeginAnimation(OpacityProperty, new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(120)));
        PaletteScrim.BeginAnimation(OpacityProperty, new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(120)));
    }

    private void OnPaletteKey(object sender, KeyEventArgs e)
    {
        switch (e.Key)
        {
            case Key.Down:
                Places.MoveSelection(1);
                PaletteList.ScrollIntoView(PaletteList.SelectedItem);
                e.Handled = true;
                break;
            case Key.Up:
                Places.MoveSelection(-1);
                PaletteList.ScrollIntoView(PaletteList.SelectedItem);
                e.Handled = true;
                break;
            case Key.Enter:
                Places.ChooseCommand.Execute(null);
                e.Handled = true;
                break;
            case Key.Escape:
                Places.ClosePaletteCommand.Execute(null);
                e.Handled = true;
                break;
        }
    }

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
            AppPage.Wants => typeof(WantsPage),
            AppPage.Tally => typeof(TallyPage),
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
