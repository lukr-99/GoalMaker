using System.IO;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using GoalMaker.App.Localization;
using GoalMaker.App.Theming;
using GoalMaker.App.ViewModels;
using GoalMaker.App.Views;
using GoalMaker.Core.Composer;
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
            planner.Tasks, planner.Areas, planner.Tags, planner.Settings, strings, planner.Time, theme.AreaBrush, day => day.AddDays(1), action => action(), () => plan?.Start());
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
            planner.Tasks, planner.Areas, planner.Tags, planner.Settings, strings, planner.Time, theme.AreaBrush, day => day, action => action(), () => { });
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
            planner.Tasks, planner.Areas, planner.Tags, planner.Settings, strings, planner.Time, theme.AreaBrush, _ => null, action => action());
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
        planner.Areas.SetEmoji(planner.Areas.Find("Home")!.Id, "🏠");
        using var theme = Theme(planner);
        Save(new AreasPage(new AreasViewModel(planner.Areas, planner.Tags, strings, theme.AreaBrush, action => action())), folder, "areas-and-tags");

        var filter = new ListFilterState();
        filter.ToggleArea(planner.Areas.Find("Home")!.Id);
        var composer = new ComposerViewModel(
            planner.Tasks, planner.Areas, planner.Tags, planner.Settings, strings, planner.Time, theme.AreaBrush, day => day, action => action(), () => { });
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
            filter: filter);
        Save(new TodayPage(today), folder, "today-filtered");
    });

    private static void Add(TestPlanner planner, string line, DateOnly? day)
    {
        var draft = ComposerParser.Parse(line, planner.Time.GetLocalNow().DateTime) with { PlannedDate = day };
        Assert.NotNull(planner.Tasks.Add(draft));
        planner.Time.Advance(TimeSpan.FromSeconds(1));
    }

    private static ThemeApplier Theme(TestPlanner planner)
    {
        var theme = new ThemeApplier(ContractResources.Themes(), Application.Current.Resources);
        theme.Apply(planner.Settings.Appearance);
        return theme;
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
