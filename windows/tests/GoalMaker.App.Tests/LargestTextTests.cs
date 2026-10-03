using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using GoalMaker.App.Shell;
using GoalMaker.App.Startup;
using GoalMaker.App.Theming;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>
/// Windows' largest text size (225%, M6-05): the windows grow with it and their content scales as a
/// whole, and at the smallest a window may get, every control outside a scrolling list is still
/// inside it, so nothing is lost off the edge that scrolling cannot bring back.
/// </summary>
[Collection(nameof(WpfCollection))]
public sealed class LargestTextTests : IDisposable
{
    private static readonly DateOnly Today = new(2026, 9, 18);
    private readonly TestPlanner planner = new();

    public void Dispose() => planner.Dispose();

    [Theory]
    [InlineData(0.5, 1)]
    [InlineData(1, 1)]
    [InlineData(1.5, 1.5)]
    [InlineData(2.25, 2.25)]
    [InlineData(3, 2.25)]
    [InlineData(double.NaN, 1)]
    public void AFactorStaysWithinWhatWindowsOffers(double reported, double kept) => Assert.Equal(kept, TextScale.Clamp(reported));

    [Theory]
    [InlineData(380, 1, 1000, 380)]
    [InlineData(380, 2.25, 1000, 855)]
    [InlineData(560, 2.25, 1040, 1040)]
    [InlineData(380, 2.25, 300, 380)]
    public void AWindowGrowsWithTheTextAsFarAsTheScreenAllows(double length, double factor, double room, double grown) =>
        Assert.Equal(grown, TextScale.Grow(length, factor, room));

    [Fact]
    public void ContentFollowsTheTextSizeWhileItIsShown() => WpfApp.Run(() =>
    {
        var scale = new TextScale(action => action(), readSystem: false);
        var content = new Border();
        scale.Follow(content);
        Assert.Same(Transform.Identity, content.LayoutTransform);

        using var host = TabOrder.Host(content, 100, 100);
        scale.Set(2.25);

        var grown = Assert.IsType<ScaleTransform>(content.LayoutTransform);
        Assert.Equal((2.25, 2.25), (grown.ScaleX, grown.ScaleY));
        scale.Set(1);
        Assert.Same(Transform.Identity, content.LayoutTransform);
    });

    [Fact]
    public void AMiniWindowGrowsWithTheText() => WpfApp.Run(() =>
    {
        var window = Mini(MiniPage.Habits, TextScale.Largest).Window;
        var area = SystemParameters.WorkArea;

        Assert.Equal(TextScale.Grow(260, TextScale.Largest, area.Width), window.MinWidth);
        Assert.Equal(TextScale.Grow(320, TextScale.Largest, area.Height), window.MinHeight);
        Assert.Equal(TextScale.Grow(MiniWindow.DefaultSize.Width, TextScale.Largest, area.Width), window.Width);
    });

    [Theory]
    [InlineData(1, false)]
    [InlineData(1, true)]
    [InlineData(TextScale.Largest, false)]
    [InlineData(TextScale.Largest, true)]
    public void TheTodayMiniWindowKeepsItsControlsAtItsSmallest(double factor, bool filtering) => WpfApp.Run(() =>
    {
        Add("Phone the insurance about the windscreen claim before the office closes 17:00 @Home #money", Today);
        Add("Stretch", Today);
        var (frame, window) = Mini(MiniPage.Today, factor, filtering);
        using var host = TabOrder.Host(frame, window.MinWidth, window.MinHeight);

        var stops = TabOrder.Stops(frame).Select(TabOrder.Name).ToList();
        Assert.Empty(Outside(frame));
        Assert.Contains("Composer.Placeholder", stops);
        Assert.Contains("Plan tomorrow", stops);
        Assert.Equal(filtering, stops.Contains("Show everything"));
    });

    [Fact]
    public void TheHabitsMiniWindowKeepsItsControlsAtItsSmallest() => WpfApp.Run(() =>
    {
        planner.Habits.Add(new HabitDraft("Read a chapter of something that is not a screen", Today.AddDays(-10)));
        var (frame, window) = Mini(MiniPage.Habits, TextScale.Largest);
        using var host = TabOrder.Host(frame, window.MinWidth, window.MinHeight);

        Assert.Empty(Outside(frame));
    });

    [Fact]
    public void TheTrayFlyoutKeepsItsButtonsAtTheLargestText() => WpfApp.Run(() =>
    {
        Add("Phone the insurance about the windscreen claim before the office closes", Today);
        var scale = new TextScale(action => action(), readSystem: false);
        scale.Set(TextScale.Largest);
        var flyout = new TrayFlyout(new TrayFlyoutViewModel(
            planner.Tasks, planner.Areas, planner.Settings, planner.Strings, planner.Time, _ => null, action => action(), () => { }, () => { }));
        scale.Follow(flyout);
        using var host = TabOrder.Host(flyout, 340 * TextScale.Largest, 1000);

        Assert.Equal(340 * TextScale.Largest, flyout.DesiredSize.Width, 0.5);
        Assert.Empty(Outside(flyout));
    });

    [Fact]
    public void TheQuickAddBoxKeepsItsControlsAtTheLargestText() => WpfApp.Run(() =>
    {
        var scale = new TextScale(action => action(), readSystem: false);
        scale.Set(TextScale.Largest);
        var composer = new ComposerViewModel(
            planner.Tasks, planner.Areas, planner.Tags, planner.Projects, planner.Settings, planner.Strings, planner.Time, _ => null, _ => null, action => action());
        composer.NewTaskTitle = "Call the bank tomorrow 17:00 #money @Home every monday !";
        var box = new QuickAddWindow(composer, planner.Strings, scale);
        var content = (FrameworkElement)box.Content;
        box.Content = null;
        // A 1280 wide screen at 150%: the box is as wide as the screen allows, not 225% of its width.
        using var host = TabOrder.Host(content, TextScale.Grow(592, TextScale.Largest, 1280), 900);

        Assert.Empty(Outside(content));
        box.CloseForGood();
    });

    // Every stop Tab reaches outside a scrolling list whose bounds reach past the root's.
    private static List<string> Outside(FrameworkElement root)
    {
        var bounds = new Rect(0, 0, root.ActualWidth * Scale(root), root.ActualHeight * Scale(root));
        return [.. TabOrder.Stops(root)
            .OfType<FrameworkElement>()
            .Where(stop => !InScrollingList(stop, root))
            .Where(stop =>
            {
                var place = stop.TransformToAncestor(root).TransformBounds(new Rect(stop.RenderSize));
                place = root.LayoutTransform.TransformBounds(place);
                return place.Left < -0.5 || place.Top < -0.5 || place.Right > bounds.Right + 0.5 || place.Bottom > bounds.Bottom + 0.5;
            })
            .Select(TabOrder.Name)];
    }

    private static double Scale(FrameworkElement root) => root.LayoutTransform is ScaleTransform scale ? scale.ScaleX : 1;

    private static bool InScrollingList(DependencyObject element, DependencyObject root)
    {
        for (var at = VisualTreeHelper.GetParent(element); at is not null && at != root; at = VisualTreeHelper.GetParent(at))
        {
            if (at is ScrollViewer)
            {
                return true;
            }
        }

        return false;
    }

    private (FrameworkElement Frame, MiniWindow Window) Mini(MiniPage page, double factor, bool filtering = false)
    {
        var scale = new TextScale(action => action(), readSystem: false);
        scale.Set(factor);
        var composer = new ComposerViewModel(
            planner.Tasks, planner.Areas, planner.Tags, planner.Projects, planner.Settings, planner.Strings, planner.Time, _ => null, day => day, action => action());
        var filter = new ListFilterState();
        var filters = new ListFiltersViewModel(planner.Areas, planner.Tags, filter, planner.Strings, _ => null, action => action());
        var today = new ListViewModel(
            ListKind.Today,
            planner.Tasks,
            planner.Areas,
            composer,
            planner.Sync,
            planner.Settings,
            planner.Strings,
            planner.Time,
            _ => null,
            () => true,
            planner.Tick,
            action => action(),
            tags: planner.Tags,
            filter: filter,
            filters: filters);
        if (filtering)
        {
            filters.SelectedArea = filters.AreaChoices[^1];
        }

        var habits = new HabitsViewModel(planner.Habits, planner.Goals, planner.Settings, planner.Strings, planner.Time, () => true, action => action());
        var window = new MiniWindow(MiniWindowContent.For(page, today, habits), planner.Strings, planner.Settings, scale, () => { });
        var frame = (FrameworkElement)window.Content;
        window.Content = null;
        return (frame, window);
    }

    private void Add(string line, DateOnly? day)
    {
        var draft = ComposerParser.Parse(line, planner.Time.GetLocalNow().DateTime) with { PlannedDate = day };
        Assert.NotNull(planner.Tasks.Add(draft));
        planner.Time.Advance(TimeSpan.FromSeconds(1));
    }
}
