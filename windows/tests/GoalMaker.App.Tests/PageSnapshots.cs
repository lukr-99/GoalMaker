using System.IO;
using System.Text.Json.Nodes;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using GoalMaker.App.Localization;
using GoalMaker.App.Theming;
using GoalMaker.App.ViewModels;
using GoalMaker.App.Views;
using GoalMaker.Core.Activity;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Connector;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;
using GoalMaker.Infrastructure.Sync;
using Wpf.Ui.Appearance;
using Wpf.Ui.Markup;

namespace GoalMaker.App.Tests;

/// <summary>
/// Renders pages offscreen to PNG files for a look at the layout without opening a window, so nothing
/// on the desktop moves or takes focus. Explicit: run with
/// <c>dotnet test --project tests/GoalMaker.App.Tests -- --explicit only</c>; the files land in
/// GOALMAKER_SNAPSHOTS, or in goalmaker-snapshots under the temp folder.
/// </summary>
public sealed class PageSnapshots
{
    private static readonly DateOnly Today = new(2026, 9, 18);

    [Fact(Explicit = true)]
    public void PlanTomorrow() => OnUiThread(folder =>
    {
        using var planner = new TestPlanner();
        planner.Settings.Appearance = Appearance.Default with { Mode = GoalMaker.Core.Settings.ThemeMode.Dark };
        var strings = new ResourceStrings(Application.Current);
        Add(planner, "File the receipts", Today.AddDays(-2));
        Add(planner, "Review budget 18:00 @Home every monday", Today);
        Add(planner, "Call the bank", Today);
        Add(planner, "Stretch !", Today);
        Add(planner, "Pack gym bag 7:00", Today.AddDays(1));
        Add(planner, "Read about sourdough", null);
        Add(planner, "Book the dentist", null);
        using var theme = Theme(planner);
        PlanViewModel? plan = null;
        var composer = new ComposerViewModel(
            planner.Tasks, planner.Areas, planner.Tags, planner.Projects, planner.Settings, strings, planner.Time, theme.AreaBrush, day => day.AddDays(1), action => action(), () => plan?.Start());
        plan = new PlanViewModel(
            planner.Tasks, planner.Areas, composer, planner.Settings, strings, planner.Time, theme.AreaBrush, planner.Tick, _ => { }, action => action());
        var page = new PlanPage(plan);

        plan.Review[1].ChooseTomorrowCommand.Execute(null);
        plan.Review[2].PickedDate = new DateTime(2026, 9, 25);
        plan.Review[3].ChooseDoneCommand.Execute(null);
        Save(page, folder, "plan-1-today-partly-decided");

        plan.Review[0].ChooseDropCommand.Execute(null);
        plan.NextCommand.Execute(null);
        plan.Tomorrow.First(row => row.Title == "Review budget").ToggleCommand.Execute(null);
        plan.IsInboxExpanded = true;
        composer.NewTaskTitle = "Water plants tomorrow #home";
        Save(page, folder, "plan-2-tomorrow");

        plan.NextCommand.Execute(null);
        Save(page, folder, "plan-3-done");
    });

    [Fact(Explicit = true)]
    public void TodayWithAReminder() => OnUiThread(folder =>
    {
        using var planner = new TestPlanner();
        var strings = new ResourceStrings(Application.Current);
        Add(planner, "Call the bank 17:00", Today);
        Add(planner, "Stretch !", Today);
        Add(planner, "Buy milk", Today);
        using var theme = Theme(planner);
        var reminders = new ReminderService(planner.Reminders, planner.Tasks, new Unarmed(), planner.Settings, planner.Time);
        reminders.AddBefore(planner.Task("Call the bank").Id, 15);
        var composer = new ComposerViewModel(
            planner.Tasks, planner.Areas, planner.Tags, planner.Projects, planner.Settings, strings, planner.Time, theme.AreaBrush, day => day, action => action(), () => { });
        var today = new ListViewModel(
            ListKind.Today,
            planner.Tasks,
            planner.Areas,
            composer,
            planner.Sync,
            planner.Settings,
            strings,
            planner.Time,
            theme.AreaBrush,
            () => true,
            planner.Tick,
            action => action(),
            reminders: reminders);
        Save(new TodayPage(today), folder, "today-with-a-reminder");
    });

    [Fact(Explicit = true)]
    public void TrayAndQuickAdd() => OnUiThread(folder =>
    {
        using var planner = new TestPlanner();
        var strings = new ResourceStrings(Application.Current);
        Add(planner, "File the receipts", Today.AddDays(-1));
        Add(planner, "Stretch !", Today);
        Add(planner, "Call the bank 17:00", Today);
        Add(planner, "Buy milk", Today);
        using var theme = Theme(planner);
        var flyout = new TrayFlyoutViewModel(
            planner.Tasks, planner.Areas, planner.Settings, strings, planner.Time, theme.AreaBrush, action => action(), () => { }, () => { });
        flyout.Rows[1].IsDone = true;
        Save(new Shell.TrayFlyout(flyout), folder, "tray-flyout", new Size(340, 420));

        var composer = new ComposerViewModel(
            planner.Tasks, planner.Areas, planner.Tags, planner.Projects, planner.Settings, strings, planner.Time, theme.AreaBrush, _ => null, action => action());
        composer.NewTaskTitle = "Look up train times tomorrow 9:00 #travel";
        var box = new Shell.QuickAddWindow(composer, strings);
        var content = (FrameworkElement)box.Content;
        box.Content = null;
        Save(content, folder, "quick-add", new Size(560, 150));
    });

    [Fact(Explicit = true)]
    public void AreasAndAFilteredList() => OnUiThread(folder =>
    {
        using var planner = new TestPlanner();
        var strings = new ResourceStrings(Application.Current);
        Add(planner, "Fix the shelf @Home #weekend", Today);
        Add(planner, "Send the invoice @Work", Today);
        Add(planner, "Buy stamps @Home #errand", Today);
        planner.Areas.Create("Health");
        planner.Areas.Archive(planner.Areas.Create("Garden")!.Id);
        planner.Areas.SetEmoji(planner.Areas.Find("Home")!.Id, "🏠");
        using var theme = Theme(planner);
        Save(new AreasPage(new AreasViewModel(planner.Areas, planner.Tags, strings, theme.AreaBrush, action => action())), folder, "areas-and-tags");

        var filter = new ListFilterState();
        filter.SetArea(planner.Areas.Find("Home")!.Id);
        var composer = new ComposerViewModel(
            planner.Tasks, planner.Areas, planner.Tags, planner.Projects, planner.Settings, strings, planner.Time, theme.AreaBrush, day => day, action => action(), () => { });
        var today = new ListViewModel(
            ListKind.Today,
            planner.Tasks,
            planner.Areas,
            composer,
            planner.Sync,
            planner.Settings,
            strings,
            planner.Time,
            theme.AreaBrush,
            () => true,
            planner.Tick,
            action => action(),
            tags: planner.Tags,
            filter: filter,
            filters: new ListFiltersViewModel(planner.Areas, planner.Tags, filter, strings, theme.AreaBrush, action => action()),
            goals: planner.Goals);
        planner.Goals.Add(new GoalDraft("3 runs", GoalHorizon.Week, Today, GoalRules.ModeTasks));
        planner.Goals.Add(new GoalDraft("Book the dentist", GoalHorizon.Week, Today));
        today.IsWeekGoalsExpanded = true;
        Save(new TodayPage(today), folder, "today-filtered-dark");
        theme.Apply(planner.Settings.Appearance with { Mode = GoalMaker.Core.Settings.ThemeMode.Light });
        Save(new TodayPage(today), folder, "today-filtered-light");
    });

    [Fact(Explicit = true)]
    public void TaskDetailsAndTheArchive() => OnUiThread(folder =>
    {
        using var planner = new TestPlanner();
        planner.Settings.Appearance = Appearance.Default with { Mode = GoalMaker.Core.Settings.ThemeMode.Dark };
        var strings = new ResourceStrings(Application.Current);
        Add(planner, "Book the train to Brno 18:00 @Home #travel", Today.AddDays(2));
        var task = planner.Task("Book the train to Brno");
        planner.Tasks.SetNotes(task.Id, "Check the **passport** first.\n- Window seat\n- *Quiet* coach\nTimes at https://www.cd.cz/en/.");
        planner.Tasks.SetDeadline(task.Id, Today.AddDays(5));
        planner.Tasks.SetRecurrence(task.Id, "FREQ=WEEKLY;BYDAY=SU");
        planner.Tags.FindOrCreate("weekend");
        planner.Steps.Add(task.Id, "Compare prices");
        planner.Steps.SetDone(planner.Steps.Add(task.Id, "Pick a seat")!.Id, true);
        using var theme = Theme(planner);
        var detail = new TaskDetailViewModel(planner.Tasks, planner.Areas, planner.Tags, planner.Steps, strings, planner.Time, action => action(), _ => { });
        detail.Load(task.Id, Startup.AppPage.Today);
        Save(new TaskPage(detail), folder, "task-details", new Size(852, 1400));

        Add(planner, "Call the bank", Today);
        Add(planner, "Buy milk", Today);
        planner.Tasks.SetDone(planner.Task("Call the bank").Id, true);
        planner.Time.Advance(TimeSpan.FromHours(2));
        planner.Tasks.SetDone(planner.Task("Buy milk").Id, true);
        var archive = new ArchiveViewModel(planner.Tasks, strings, action => action(), _ => { });
        Save(new ArchivePage(archive), folder, "archive");
    });

    [Fact(Explicit = true)]
    public void ActivityAndTheConnectorCard() => OnUiThread(folder =>
    {
        using var planner = new TestPlanner();
        planner.Settings.Appearance = Appearance.Default with { Mode = GoalMaker.Core.Settings.ThemeMode.Dark };
        var strings = new ResourceStrings(Application.Current);
        using var theme = Theme(planner);

        var activity = new ActivityViewModel(new SnapshotLog(), strings, () => { });
        activity.RefreshAsync().GetAwaiter().GetResult();
        Save(new ActivityPage(activity), folder, "activity");

        var connector = new ConnectorViewModel(new SnapshotLinks(), "https://example.supabase.co", strings, _ => { });
        connector.CreateCommand.ExecuteAsync(null).GetAwaiter().GetResult();
        connector.CopyCommand.Execute(null);
        Save(OnPage(new Controls.ConnectorCard { DataContext = connector }), folder, "connector-card", new Size(760, 480));
        connector.HideNewUrlCommand.Execute(null);
        connector.RevokeCommand.Execute(null);
        Save(OnPage(new Controls.ConnectorCard { DataContext = connector }), folder, "connector-card-revoking", new Size(760, 400));
    });

    private static void Add(TestPlanner planner, string line, DateOnly? day)
    {
        var draft = ComposerParser.Parse(line, planner.Time.GetLocalNow().DateTime) with { PlannedDate = day };
        Assert.NotNull(planner.Tasks.Add(draft));
        planner.Time.Advance(TimeSpan.FromSeconds(1));
    }

    [Fact(Explicit = true)]
    public void GoalsPageAndEditor() => OnUiThread(folder =>
    {
        using var planner = new TestPlanner();
        var strings = new ResourceStrings(Application.Current);
        var year = planner.Goals.Add(new GoalDraft("Run a half marathon", GoalHorizon.Year, Today, Emoji: "🏃"))!;
        var month = planner.Goals.Add(new GoalDraft("Run 80 km", GoalHorizon.Month, Today, GoalRules.ModeNumber, ParentId: year.Id, Target: 80, Unit: "km"))!;
        planner.Goals.LogAmount(month.Id, Today, 32.5);
        var week = planner.Goals.Add(new GoalDraft("3 runs this week", GoalHorizon.Week, Today, GoalRules.ModeTasks, ParentId: month.Id))!;
        Add(planner, "Morning run", Today);
        Add(planner, "Long run", Today.AddDays(1));
        planner.Tasks.SetGoal(planner.Task("Morning run").Id, week.Id);
        planner.Tasks.SetGoal(planner.Task("Long run").Id, week.Id);
        planner.Tasks.SetDone(planner.Task("Morning run").Id, true);
        var book = planner.Goals.Add(new GoalDraft("Book the dentist", GoalHorizon.Week, Today))!;
        planner.Goals.SetStatus(book.Id, GoalRules.Done);
        planner.Goals.Add(new GoalDraft("Inbox zero", GoalHorizon.Day, Today));
        using var theme = Theme(planner);
        var goals = new GoalsViewModel(planner.Goals, planner.Tasks, planner.Settings, strings, planner.Time, () => true, action => action());
        var page = new GoalsPage(goals);
        Save(page, folder, "goals-by-period", new Size(852, 1100));

        goals.ToggleTreeCommand.Execute(null);
        Save(page, folder, "goals-tree", new Size(852, 672));

        goals.ToggleTreeCommand.Execute(null);
        goals.Edit(planner.Goals.Find(month.Id)!);
        Save(page, folder, "goals-editor", new Size(852, 760));
    });

    [Fact(Explicit = true)]
    public void HabitsPageAndEditor() => OnUiThread(folder =>
    {
        using var planner = new TestPlanner();
        var strings = new ResourceStrings(Application.Current);
        var goal = planner.Goals.Add(new GoalDraft("Run 80 km", GoalHorizon.Month, Today, GoalRules.ModeNumber, Target: 80, Unit: "km"))!;
        var read = planner.Habits.Add(new HabitDraft("Read before bed", Today.AddDays(-120)) { Emoji = "📖" })!;
        var water = planner.Habits.Add(new HabitDraft("Drink water", Today.AddDays(-60)) { Measure = HabitRules.Count, Target = 8, Unit = "glasses" })!;
        var run = planner.Habits.Add(new HabitDraft("Run", Today.AddDays(-90))
        {
            Cadence = HabitRules.PerWeek,
            Times = 3,
            Measure = HabitRules.Amount,
            Target = 5,
            Unit = "km",
            GoalId = goal.Id,
            Emoji = "🏃",
        })!;
        var gym = planner.Habits.Add(new HabitDraft("Gym", Today.AddDays(-45)) { Cadence = HabitRules.OnWeekdays, Weekdays = 21 })!;
        // A few months of history: most days read, water and runs often, the gym on its days.
        for (var back = 120; back >= 0; back--)
        {
            var day = Today.AddDays(-back);
            if (back % 9 != 3)
            {
                planner.Habits.CheckIn(read.Id, day);
            }

            if (back % 4 != 1 && day >= Today.AddDays(-60))
            {
                planner.Habits.CheckIn(water.Id, day, back % 3 == 0 ? 8 : 5);
            }

            if (back % 3 == 0 && day >= Today.AddDays(-90))
            {
                planner.Habits.CheckIn(run.Id, day, 6);
            }

            if (HabitRules.IsDue(gym, day) && back % 5 != 0 && day >= Today.AddDays(-45))
            {
                planner.Habits.CheckIn(gym.Id, day);
            }
        }

        planner.Habits.Skip(read.Id, Today.AddDays(-14));
        planner.Habits.Pause(gym.Id, Today.AddDays(-9));
        planner.Habits.Resume(gym.Id, Today.AddDays(-4));
        using var theme = Theme(planner);
        var habits = new HabitsViewModel(planner.Habits, planner.Goals, planner.Settings, strings, planner.Time, () => true, action => action());
        var page = new HabitsPage(habits);
        Save(page, folder, "habits", new Size(852, 900));

        habits.Edit(planner.Habits.Find(run.Id)!);
        Save(page, folder, "habits-editor", new Size(852, 1000));
    });

    [Fact(Explicit = true)]
    public void CalendarPage_() => OnUiThread(folder =>
    {
        using var planner = new TestPlanner();
        var strings = new ResourceStrings(Application.Current);
        using var theme = Theme(planner);
        foreach (var (line, day) in new[]
        {
            ("Call the bank 9:00", Today),
            ("Water the plants", Today),
            ("File the receipts", Today.AddDays(-3)),
            ("Pack the gym bag 7:00", Today.AddDays(1)),
            ("Book the dentist", Today.AddDays(4)),
            ("Read about sourdough", Today.AddDays(4)),
            ("Fix the bike", Today.AddDays(4)),
        })
        {
            var task = planner.Tasks.Add(ComposerParser.Parse(line, planner.Time.GetLocalNow().DateTime))!;
            planner.Tasks.Plan(task.Id, day);
        }

        planner.Tasks.SetDeadline(planner.Task("Book the dentist").Id, Today.AddDays(9));
        planner.Tasks.SetRecurrence(planner.Task("Water the plants").Id, "FREQ=DAILY");

        var calendar = new CalendarViewModel(
            planner.Tasks, planner.Reminders, planner.Settings, strings, planner.Time, _ => { }, action => action());
        calendar.Open(Today);
        Save(new CalendarPage(calendar), folder, "calendar", new Size(1000, 620));
    });

    [Fact(Explicit = true)]
    public void ProjectsPage_() => OnUiThread(folder =>
    {
        using var planner = new TestPlanner();
        var strings = new ResourceStrings(Application.Current);
        using var theme = Theme(planner);
        var projects = new ProjectsViewModel(planner.Projects, planner.Tasks, strings, _ => { }, action => action());
        projects.NewCommand.Execute(null);
        projects.ProjectName = "GoalMaker";
        projects.ProjectDescription = "The planner on the phone and the PC.";
        projects.ProjectRepository = "https://github.com/owner/goalmaker";
        projects.ProjectFolder = @"F:\Code\GoalMaker";
        projects.SaveCommand.Execute(null);
        foreach (var (title, type) in new[]
        {
            ("Widgets for habits", ProjectRules.Idea),
            ("Share to GoalMaker", ProjectRules.Idea),
            ("The calendar view", ProjectRules.Task),
            ("Mini windows", ProjectRules.Task),
            ("The board drags nothing yet", ProjectRules.Bug),
        })
        {
            projects.NewItemType = type;
            projects.NewItemTitle = title;
            projects.AddItemCommand.Execute(null);
        }

        planner.Tasks.SetPriority(planner.Task("The board drags nothing yet").Id, ProjectRules.Urgent);
        planner.Tasks.SetBoardColumn(planner.Task("The calendar view").Id, ProjectRules.Doing);
        planner.Tasks.SetBoardColumn(planner.Task("Mini windows").Id, ProjectRules.Done);
        projects.Refresh();

        Save(new ProjectsPage(projects), folder, "projects", new Size(1100, 700));
    });

    [Fact(Explicit = true)]
    public void ReviewPages() => OnUiThread(folder =>
    {
        using var planner = new TestPlanner();
        planner.Time.SetUtcNow(new DateTimeOffset(2026, 9, 21, 9, 0, 0, TimeSpan.Zero));
        var strings = new ResourceStrings(Application.Current);
        var weekStart = new DateOnly(2026, 9, 14);
        var area = planner.Areas.FindOrCreate("Work")!;
        for (var back = 0; back < 7; back++)
        {
            var day = weekStart.AddDays(back);
            for (var count = 0; count < (back % 3) + 1; count++)
            {
                var task = planner.Tasks.Add(ComposerParser.Parse($"Task {back}-{count}", planner.Time.GetLocalNow().DateTime))!;
                planner.Tasks.SetArea(task.Id, area.Id);
                planner.Tasks.SetDone(task.Id, true);
                if (planner.Replica.Get("tasks", task.Id) is { } row)
                {
                    row["completed_at"] = JsonValue.Create($"{day:yyyy-MM-dd}T18:00:00.000000Z");
                    planner.Replica.Queue("tasks", row);
                }

                planner.Time.Advance(TimeSpan.FromSeconds(1));
            }
        }

        var goal = planner.Goals.Add(new GoalDraft("Run 20 km", GoalHorizon.Week, weekStart, GoalRules.ModeNumber, Target: 20, Unit: "km"))!;
        planner.Goals.LogAmount(goal.Id, weekStart.AddDays(2), 12);
        var habit = planner.Habits.Add(new HabitDraft("Read before bed", weekStart.AddDays(-60)) { Emoji = "📖" })!;
        for (var back = 0; back < 20; back++)
        {
            planner.Habits.CheckIn(habit.Id, new DateOnly(2026, 9, 20).AddDays(-back));
        }

        var left = planner.Tasks.Add(ComposerParser.Parse("Call the bank", planner.Time.GetLocalNow().DateTime))!;
        planner.Tasks.Plan(left.Id, weekStart.AddDays(3));
        using var theme = Theme(planner);
        var prompts = ContractResources.Prompts();
        var review = new ReviewViewModel(
            ReviewRules.Weekly,
            weekStart,
            planner.Reviews,
            planner.Tasks,
            planner.Areas,
            planner.Goals,
            planner.Habits,
            prompts,
            planner.Rituals,
            planner.Settings,
            strings,
            planner.Time,
            action => action());
        var page = new ReviewPage(review);
        Save(page, folder, "review-look-back", new Size(900, 900));

        review.NextCommand.Execute(null);
        Save(page, folder, "review-open-tasks", new Size(900, 520));
        review.NextCommand.Execute(null);
        Save(page, folder, "review-reflect", new Size(900, 760));
        review.NextCommand.Execute(null);
        review.SetMood(4);
        review.SetEnergy(3);
        Save(page, folder, "review-rate", new Size(900, 480));

        var list = new ReviewsViewModel(planner.Reviews, planner.Settings, strings, planner.Time, (_, _) => { }, action => action());
        Save(new ReviewsPage(list), folder, "reviews", new Size(900, 700));

        // A few weeks of ratings behind the current one, so the chart has a line to draw.
        foreach (var (back, mood, energy) in new[] { (4, 3, 2), (3, 4, 4), (2, 2, 3), (1, 5, 4) })
        {
            var past = planner.Reviews.Open(ReviewRules.Weekly, weekStart.AddDays(-7 * back))!;
            planner.Reviews.SetMood(past.Id, mood);
            planner.Reviews.SetEnergy(past.Id, energy);
        }

        var stats = new StatsViewModel(
            planner.Tasks, planner.Goals, planner.Habits, planner.Reviews, planner.Settings, strings, planner.Time, action => action());
        Save(new StatsPage(stats), folder, "stats", new Size(900, 1000));
    });

    // Controls made under one theme take the next theme's accent (in a window, where resource changes
    // reach them): Electric's toggles once stayed Track's lime.
    [Fact(Explicit = true)]
    public void ControlsFollowAThemeSwitch() => OnUiThread(folder =>
    {
        using var planner = new TestPlanner();
        planner.Settings.Appearance = Appearance.Default with { Mode = GoalMaker.Core.Settings.ThemeMode.Dark };
        using var theme = Theme(planner);
        var panel = new StackPanel { Orientation = Orientation.Horizontal };
        panel.Children.Add(new RadioButton { IsChecked = true, Content = "Radio", Margin = new Thickness(8) });
        panel.Children.Add(new Wpf.Ui.Controls.ToggleSwitch { IsChecked = true, Margin = new Thickness(8) });
        panel.Children.Add(new CheckBox { IsChecked = true, Content = "Check", Margin = new Thickness(8) });
        panel.Children.Add(new Wpf.Ui.Controls.Button { Appearance = Wpf.Ui.Controls.ControlAppearance.Primary, Content = "Plan tomorrow", Margin = new Thickness(8) });
        var page = OnPage(panel);
        var window = new Window { Content = page, Width = 560, Height = 140, Left = -4000, Top = 100, ShowActivated = false, ShowInTaskbar = false, WindowStyle = WindowStyle.None };
        window.Show();
        void Shot(string name)
        {
            Settle();
            page.UpdateLayout();
            Settle();
            var bitmap = new RenderTargetBitmap((int)page.ActualWidth, (int)page.ActualHeight, 96, 96, PixelFormats.Pbgra32);
            bitmap.Render(page);
            var encoder = new PngBitmapEncoder();
            encoder.Frames.Add(BitmapFrame.Create(bitmap));
            using var file = File.Create(Path.Combine(folder, name + ".png"));
            encoder.Save(file);
        }

        Shot("theme-switch-1-track");
        theme.Apply(planner.Settings.Appearance with { ThemeId = "electric" });
        Shot("theme-switch-2-electric");
        theme.Apply(planner.Settings.Appearance with { ThemeId = "sunrise", Mode = GoalMaker.Core.Settings.ThemeMode.Light });
        Shot("theme-switch-3-sunrise-light");
        window.Close();
    });

    [Fact(Explicit = true)]
    public void LogoInEveryTheme() => OnUiThread(folder =>
    {
        var tokens = ContractResources.Themes();
        var mark = ContractResources.Logo();
        var strip = new StackPanel { Orientation = Orientation.Horizontal, Background = Brushes.White };
        foreach (var theme in tokens.Themes)
        {
            strip.Children.Add(new Image { Source = Controls.GoalMakerLogo.Render(mark, theme.Logo, 128), Width = 128, Height = 128, Margin = new Thickness(8) });
        }

        Save(strip, folder, "logo-every-theme", new Size(4 * 144, 144));
    });

    private static ThemeApplier Theme(TestPlanner planner)
    {
        var theme = new ThemeApplier(ContractResources.Themes(), Application.Current.Resources, ContractResources.Logo());
        theme.Apply(planner.Settings.Appearance);
        return theme;
    }

    // A control on the theme's page background, as it sits in the app, instead of on nothing.
    private static Border OnPage(FrameworkElement control)
    {
        var page = new Border { Padding = new Thickness(16), Child = control };
        page.SetResourceReference(Border.BackgroundProperty, "GM.BackgroundBrush");
        return page;
    }

    // Lays the page out at the main window's content size, or the given one, and writes it as a PNG.
    private static void Save(FrameworkElement page, string folder, string name, Size? area = null)
    {
        var size = area ?? new Size(852, 672);
        page.Measure(size);
        page.Arrange(new Rect(size));
        Settle();
        page.UpdateLayout();
        Settle();
        var bitmap = new RenderTargetBitmap((int)size.Width, (int)size.Height, 96, 96, PixelFormats.Pbgra32);
        bitmap.Render(page);
        var encoder = new PngBitmapEncoder();
        encoder.Frames.Add(BitmapFrame.Create(bitmap));
        using var file = File.Create(Path.Combine(folder, name + ".png"));
        encoder.Save(file);
    }

    // Lets bindings, templates and layout finish what they queued.
    private static void Settle()
    {
        var frame = new DispatcherFrame();
        Dispatcher.CurrentDispatcher.BeginInvoke(DispatcherPriority.ApplicationIdle, () => frame.Continue = false);
        Dispatcher.PushFrame(frame);
    }

    private sealed class Unarmed : IReminderScheduler
    {
        public void ArmAt(DateTime at)
        {
        }

        public void Cancel()
        {
        }
    }

    // A few changes by the owner and by Claude, one of them undone, for the Activity page.
    private sealed class SnapshotLog : IActivityLog
    {
        public Task<IReadOnlyList<ActivityEntry>> RecentAsync(int limit = 60, CancellationToken cancellationToken = default)
        {
            var at = new DateTimeOffset(2026, 9, 19, 9, 0, 0, TimeSpan.Zero);
            JsonObject Task(string title, string status, string? day) =>
                new() { ["title"] = title, ["status"] = status, ["planned_date"] = day };
            return System.Threading.Tasks.Task.FromResult<IReadOnlyList<ActivityEntry>>(
            [
                new(6, "tasks", "a", "update", "claude", Task("Call the bank", "open", "2026-09-19"), Task("Call the bank", "open", "2026-09-21"), at.AddMinutes(50), null),
                new(5, "tasks", "b", "update", "owner", Task("Water the plants", "open", "2026-09-19"), Task("Water the plants", "done", "2026-09-19"), at.AddMinutes(40), null),
                new(4, "areas", "c", "update", "owner", new JsonObject { ["name"] = "Garden", ["archived_at"] = null }, new JsonObject { ["name"] = "Garden", ["archived_at"] = "2026-09-19T09:30:00+00:00" }, at.AddMinutes(30), null),
                new(3, "tasks", "d", "create", "claude", null, Task("Book the dentist", "open", "2026-09-19"), at.AddMinutes(20), at.AddMinutes(25)),
                new(2, "reviews", "e", "create", "claude", null, new JsonObject { ["kind"] = "weekly" }, at.AddMinutes(10), null),
                new(1, "tasks", "a", "create", "owner", null, Task("Call the bank", "open", "2026-09-19"), at, null),
            ]);
        }

        public Task<UndoOutcome> UndoAsync(long entryId, CancellationToken cancellationToken = default) =>
            System.Threading.Tasks.Task.FromResult(UndoOutcome.Undone);
    }

    // One link, made and used earlier, for the connector card.
    private sealed class SnapshotLinks : IConnectorLinks
    {
        private readonly List<ConnectorLink> links = [];

        public Task<IReadOnlyList<ConnectorLink>> ListAsync(CancellationToken cancellationToken = default) =>
            Task.FromResult<IReadOnlyList<ConnectorLink>>([.. links]);

        public Task<string> CreateAsync(CancellationToken cancellationToken = default)
        {
            var at = new DateTimeOffset(2026, 9, 19, 9, 0, 0, TimeSpan.Zero);
            links.Add(new ConnectorLink("link", at, at.AddMinutes(12), null));
            return Task.FromResult("Mz3dKq0pX8vYt2LwR5nS7cJ1hG4fB6eA9uD0iO_-kPq");
        }

        public Task RevokeAsync(CancellationToken cancellationToken = default) => Task.CompletedTask;
    }

    // WPF needs one STA thread with an Application holding the app's resources; nothing is shown.
    private static void OnUiThread(Action<string> render)
    {
        var folder = Environment.GetEnvironmentVariable("GOALMAKER_SNAPSHOTS") is { Length: > 0 } configured
            ? configured
            : Path.Combine(Path.GetTempPath(), "goalmaker-snapshots");
        Directory.CreateDirectory(folder);
        Exception? failure = null;
        var thread = new Thread(() =>
        {
            try
            {
                var app = Application.Current ?? new Application { ShutdownMode = ShutdownMode.OnExplicitShutdown };
                var resources = app.Resources.MergedDictionaries;
                resources.Add(new ThemesDictionary { Theme = ApplicationTheme.Dark });
                resources.Add(new ControlsDictionary());
                foreach (var name in new[] { "Strings", "Tokens", "Converters", "ComposerTemplate", "ListTemplate" })
                {
                    resources.Add(new ResourceDictionary { Source = new Uri($"pack://application:,,,/GoalMaker;component/Resources/{name}.xaml") });
                }

                render(folder);
            }
            catch (Exception exception)
            {
                failure = exception;
            }
        });
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
        thread.Join();
        if (failure is not null)
        {
            throw new InvalidOperationException("Rendering failed", failure);
        }
    }
}
