using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using GoalMaker.App.Shell;
using GoalMaker.App.Startup;
using GoalMaker.App.Theming;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>
/// The keyboard on Windows (M6-05): the composer, the tray flyout, the mini windows and Today's habit
/// panel are reached by Tab in the order they read, every stop has a name, each window starts the
/// keyboard somewhere useful, and Esc steps back one thing at a time.
/// </summary>
[Collection(nameof(WpfCollection))]
public sealed class KeyboardReachTests : IDisposable
{
    private static readonly DateOnly Today = new(2026, 9, 18);
    private readonly TestPlanner planner = new();

    public void Dispose() => planner.Dispose();

    [Fact]
    public void TheComposerIsReachedInTheOrderItReads() => WpfApp.Run(() =>
    {
        var composer = Composer();
        composer.NewTaskTitle = "Call the bank tomorrow #home";
        var bar = Bar(composer);
        using var window = TabOrder.Host(bar, 560, 200);

        var stops = TabOrder.Stops(bar);

        // The chips' remove buttons sit above the line, then the line, then the send arrow at its end.
        Assert.Equal(["Composer.Remove(Composer.Tomorrow)", "Composer.Remove(#home)", "Composer.Placeholder", "Composer.Add"], stops.Select(TabOrder.Name));
        Assert.IsAssignableFrom<TextBoxBase>(stops[2]);
    });

    [Fact]
    public void EscClearsTheLineAndOnAnEmptyLineIsLeftToTheWindow() => WpfApp.Run(() =>
    {
        var composer = Composer();
        composer.NewTaskTitle = "Stretch";
        var bar = Bar(composer);
        using var window = TabOrder.Host(bar, 560, 200);
        var line = TabOrder.Stops(bar).OfType<TextBoxBase>().Single();

        Assert.True(PressEscape(line));
        Assert.Equal(string.Empty, composer.NewTaskTitle);
        // Nothing left to clear: the key goes on up, where a mini window closes on it.
        Assert.False(PressEscape(line));
    });

    [Fact]
    public void TheTrayFlyoutGoesRoundTheTasksThenItsButtons() => WpfApp.Run(() =>
    {
        Add("Stretch", Today);
        Add("Call the bank 17:00", Today);
        var today = Flyout();
        var flyout = new TrayFlyout(today);
        using var window = TabOrder.Host(flyout, 340, 400);

        var stops = TabOrder.Stops(flyout);

        Assert.Equal(["Call the bank", "Stretch"], today.Rows.Select(row => row.Title));
        Assert.Equal(["Call the bank", "Stretch", "Add a task", "Open GoalMaker"], stops.Select(TabOrder.Name));
        Assert.Equal(KeyboardNavigationMode.Cycle, KeyboardNavigation.GetTabNavigation(flyout));
    });

    [Fact]
    public void TheTodayMiniWindowReadsTitleBarTasksThenComposerAndStartsOnTheFirstTask() => WpfApp.Run(() =>
    {
        Add("Stretch !", Today);
        Add("Call the bank", Today);
        var (content, frame, window) = Mini(MiniPage.Today);
        using var host = TabOrder.Host(frame, 380, 560);

        var stops = TabOrder.Stops(frame);
        var start = KeyboardStart.Find(frame, content.IsStart);

        // Each task row reads left to right: its done box, then Delete (Open needs the main window).
        Assert.Equal(
            ["Mini.Pin", "Mini.Open", "Mini.Close", "Plan tomorrow", "Stretch", "Delete task", "Call the bank", "Delete task", "Composer.Placeholder", "Composer.NewTask"],
            stops.Select(TabOrder.Name));
        Assert.Same(stops[4], start);
        GC.KeepAlive(window);
    });

    [Fact]
    public void AnEmptyTodayMiniWindowStartsInTheComposer() => WpfApp.Run(() =>
    {
        var (content, frame, window) = Mini(MiniPage.Today);
        using var host = TabOrder.Host(frame, 380, 560);

        var start = KeyboardStart.Find(frame, content.IsStart);

        Assert.Equal("Composer.Placeholder", TabOrder.Name(Assert.IsAssignableFrom<TextBoxBase>(start)));
        GC.KeepAlive(window);
    });

    [Fact]
    public void TheHabitsMiniWindowStartsOnTheFirstCheckIn() => WpfApp.Run(() =>
    {
        planner.Habits.Add(new HabitDraft("Read before bed", Today.AddDays(-10)));
        planner.Habits.Add(new HabitDraft("Stretch", Today.AddDays(-10)));
        var (content, frame, window) = Mini(MiniPage.Habits);
        using var host = TabOrder.Host(frame, 380, 560);

        var stops = TabOrder.Stops(frame);
        var start = KeyboardStart.Find(frame, content.IsStart);

        Assert.Equal(3 + 2, stops.Count);
        Assert.Same(stops[3], start);
        Assert.Equal("Read before bed", ((HabitRowViewModel)((FrameworkElement)start!).DataContext).Name);
        GC.KeepAlive(window);
    });

    [Fact]
    public void ThePinSaysWhatItWouldDoNow() => WpfApp.Run(() =>
    {
        var (_, frame, window) = Mini(MiniPage.Habits);
        using var host = TabOrder.Host(frame, 380, 560);
        var pin = (ButtonBase)TabOrder.Stops(frame)[0];

        pin.RaiseEvent(new RoutedEventArgs(ButtonBase.ClickEvent));

        Assert.True(window.Topmost);
        Assert.Equal("Mini.Unpin", TabOrder.Name(pin));
        pin.RaiseEvent(new RoutedEventArgs(ButtonBase.ClickEvent));
        Assert.Equal("Mini.Pin", TabOrder.Name(pin));
    });

    [Fact]
    public void TodaysHabitPanelReadsHideDoneBeforeOpenThenTheCards() => WpfApp.Run(() =>
    {
        planner.Habits.Add(new HabitDraft("Read before bed", Today.AddDays(-10)));
        var today = TodayList(Habits());
        var list = new ContentControl { Content = today, Focusable = false };
        list.SetResourceReference(ContentControl.ContentTemplateProperty, "ListTemplate");
        using var host = TabOrder.Host(list, 380, 900);

        var names = TabOrder.Stops(list).Select(TabOrder.Name).ToList();

        var hide = names.IndexOf(today.HideDoneText);
        Assert.True(hide >= 0, string.Join(", ", names));
        Assert.Equal("Open Habits", names[hide + 1]);
        Assert.Equal(today.ShownHabits[0].ButtonText, names[hide + 2]);
    });

    [Fact]
    public void LifeGoalCardsAreReachedInOrderAndEnterOpensTheEditor() => WpfApp.Run(() =>
    {
        planner.LifeGoals.Add(new LifeGoalDraft("Own an Audi R8", "Proof", Today.AddYears(10)));
        planner.LifeGoals.Add(new LifeGoalDraft("Run a marathon", "To know I can"));
        var lifeGoals = new LifeGoalsViewModel(
            planner.LifeGoals,
            new LifeGoalPictures(planner.LifeGoals, new NoPictureFiles(), new NoPictureCloud(), () => null, planner.Time, () => { }),
            planner.Settings,
            planner.Strings,
            planner.Time,
            action => action(),
            _ => null,
            () => []);
        var page = new Views.LifeGoalsPage(lifeGoals);
        using var host = TabOrder.Host(page, 900, 1400);

        var stops = TabOrder.Stops(page);

        // Each card, then its "more" button, in the owner's order.
        Assert.Equal(
            ["New life goal", "Own an Audi R8, LifeGoals.YearsLeft(10)", "LifeGoals.Menu(Own an Audi R8)", "Run a marathon", "LifeGoals.Menu(Run a marathon)"],
            stops.Where(stop => stop.IsVisible).Select(TabOrder.Name));
        var card = (ButtonBase)stops.Single(stop => TabOrder.Name(stop) == "Run a marathon");
        // The menu key and a right click open the card's own menu: Edit, Achieved, Drop, Move up, Delete.
        var menu = Assert.IsType<ContextMenu>(card.ContextMenu);
        menu.DataContext = card.DataContext;
        TabOrder.Settle();
        Assert.Equal(
            ["Edit", "Achieved", "Drop", "Move up", "Delete"],
            menu.Items.OfType<MenuItem>().Where(item => item.Visibility == Visibility.Visible).Select(item => (string)item.Header));

        Assert.True(Press(card, Key.Enter));

        Assert.True(lifeGoals.Editor.IsOpen);
        Assert.Equal("Run a marathon", lifeGoals.Editor.Title);
    });

    [Fact]
    public void TheCalendarsEventBarsAreReachedRowByRowAndEnterOpensOne() => WpfApp.Run(() =>
    {
        // Friday to Tuesday, across the week break, and a dinner on the Saturday under it.
        planner.Events.Add(new EventDraft("Trip", Today, Today.AddDays(4)));
        planner.Events.Add(new EventDraft("Dinner", Today.AddDays(1), Today.AddDays(1)));
        planner.Events.Add(new EventDraft("Early", Today.AddDays(-17), Today.AddDays(-17)));
        var calendar = new CalendarViewModel(
            planner.Tasks, planner.Reminders, planner.Areas, planner.Tags, planner.Projects, planner.Settings, planner.Strings, _ => null, planner.Time,
            _ => { }, action => action(), events: planner.Events);
        var page = new Views.CalendarPage(calendar);
        using var host = TabOrder.Host(page, 1000, 900);

        var bars = TabOrder.Stops(page).Where(stop => TabOrder.Name(stop).StartsWith("Calendar.EventName(", StringComparison.Ordinal)).ToList();

        // Each row's bars after its days, by lane: the trip's two pieces, the dinner between them.
        Assert.Equal(["Early", "Trip", "Dinner", "Trip"], bars.Select(bar => ((CalendarBarViewModel)((FrameworkElement)bar).DataContext).Title));
        Assert.True(Press(bars[2], Key.Enter));
        Assert.True(calendar.Editor!.IsOpen);
        Assert.Equal("Dinner", calendar.Editor.Title);
    });

    private static ContentControl Bar(ComposerViewModel composer)
    {
        var bar = new ContentControl { Content = composer, Focusable = false };
        bar.SetResourceReference(ContentControl.ContentTemplateProperty, "ComposerTemplate");
        return bar;
    }

    // Esc as the keyboard sends it; true when something took it.
    private static bool PressEscape(UIElement target) => Press(target, Key.Escape);

    private static bool Press(UIElement target, Key key)
    {
        var press = new KeyEventArgs(Keyboard.PrimaryDevice, PresentationSource.FromVisual(target)!, 0, key)
        {
            RoutedEvent = Keyboard.KeyDownEvent,
        };
        target.RaiseEvent(press);
        return press.Handled;
    }

    private sealed class NoPictureFiles : IPictureFiles
    {
        public bool Has(string id) => false;

        public byte[]? Read(string id) => null;

        public void Write(string id, byte[] bytes, bool pending)
        {
        }

        public void Delete(string id)
        {
        }

        public IReadOnlySet<string> Ids() => new HashSet<string>();

        public IReadOnlySet<string> Pending() => new HashSet<string>();

        public void Uploaded(string id)
        {
        }
    }

    private sealed class NoPictureCloud : IPictureCloud
    {
        public Task UploadAsync(string owner, string id, byte[] bytes, CancellationToken cancellationToken) => Task.CompletedTask;

        public Task<byte[]?> DownloadAsync(string owner, string id, CancellationToken cancellationToken) => Task.FromResult<byte[]?>(null);

        public Task RemoveAsync(string owner, string id, CancellationToken cancellationToken) => Task.CompletedTask;
    }

    private (MiniWindowContent Content, FrameworkElement Frame, MiniWindow Window) Mini(MiniPage page)
    {
        var content = MiniWindowContent.For(page, TodayList(), Habits());
        var window = new MiniWindow(content, planner.Strings, planner.Settings, new TextScale(action => action(), readSystem: false), () => { });
        // The window's content in a hidden host of its own: the window itself is never shown.
        var frame = (FrameworkElement)window.Content;
        window.Content = null;
        return (content, frame, window);
    }

    private ComposerViewModel Composer() => new(
        planner.Tasks, planner.Areas, planner.Tags, planner.Projects, planner.Settings, planner.Strings, planner.Time, _ => null, day => day, action => action());

    private ListViewModel TodayList(HabitsViewModel? habits = null) => new(
        ListKind.Today,
        planner.Tasks,
        planner.Areas,
        Composer(),
        planner.Sync,
        planner.Settings,
        planner.Strings,
        planner.Time,
        _ => null,
        () => true,
        planner.Tick,
        action => action(),
        habitsPage: habits,
        habitList: habits is null ? null : planner.Habits);

    private HabitsViewModel Habits() => new(
        planner.Habits, planner.Goals, planner.Settings, planner.Strings, planner.Time, () => true, action => action());

    private TrayFlyoutViewModel Flyout() => new(
        planner.Tasks, planner.Areas, planner.Settings, planner.Strings, planner.Time, _ => null, action => action(), () => { }, () => { });

    private void Add(string line, DateOnly? day)
    {
        var draft = ComposerParser.Parse(line, planner.Time.GetLocalNow().DateTime) with { PlannedDate = day };
        Assert.NotNull(planner.Tasks.Add(draft));
        planner.Time.Advance(TimeSpan.FromSeconds(1));
    }
}
