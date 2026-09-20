using GoalMaker.App.Shell;
using GoalMaker.App.Startup;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.Tests;

/// <summary>
/// The mini windows (M5-06, spec stories 80 and 82): what each one shows, that ticking and checking
/// in there are the same acts as on the page, and what it remembers between launches.
/// </summary>
public sealed class MiniWindowTests : IDisposable
{
    private static readonly DateOnly Today = new(2026, 9, 18);
    private readonly TestPlanner planner = new();

    public void Dispose() => planner.Dispose();

    [Fact]
    public void TheTodayMiniWindowShowsTodayAndTicksATaskOff()
    {
        Add("Call the bank 17:00", Today);
        Add("Stretch !", Today);
        Add("Water the plants", Today.AddDays(1));
        var content = MiniWindowContent.For(MiniPage.Today, TodayList(), Habits());

        var today = Assert.IsType<ListViewModel>(content.ViewModel);
        Assert.Equal("ListTemplate", content.TemplateKey);
        Assert.Equal("today", content.Name);
        Assert.Equal(["Stretch", "Call the bank"], today.Sections.SelectMany(section => section.Rows).Select(row => row.Title));

        today.Sections.SelectMany(section => section.Rows).First(row => row.Title == "Stretch").IsDone = true;

        Assert.Equal(TaskState.Done, planner.Task("Stretch").State);
    }

    [Fact]
    public void TheHabitsMiniWindowShowsTodaysHabitsAndChecksOneIn()
    {
        planner.Habits.Add(new HabitDraft("Read before bed", Today.AddDays(-10)) { Emoji = "📖" });
        planner.Habits.Add(new HabitDraft("Drink water", Today.AddDays(-10)) { Measure = HabitRules.Count, Target = 8, Unit = "glasses" });
        var habits = Habits();
        var content = MiniWindowContent.For(MiniPage.Habits, TodayList(), habits);

        Assert.Same(habits, content.ViewModel);
        Assert.Equal("MiniHabitsTemplate", content.TemplateKey);
        Assert.Equal("habits", content.Name);
        Assert.Equal(["Read before bed", "Drink water"], habits.Rows.Select(row => row.Name));
        Assert.All(habits.Rows, row => Assert.True(row.CanCheckIn));

        habits.Rows.First(row => row.Name == "Read before bed").CheckInCommand.Execute(null);

        Assert.True(habits.Rows.First(row => row.Name == "Read before bed").IsDone);
        Assert.Equal(1, planner.Habits.Checkins().Count(checkin => checkin.Day == Today && checkin.Value > 0));
    }

    [Fact]
    public void AMiniWindowStartsWithNothingRemembered()
    {
        Assert.Null(MiniWindowMemory.Of(planner.Settings, "today"));
        Assert.Empty(planner.Settings.MiniWindows);
    }

    [Fact]
    public void EachMiniWindowRemembersItsOwnPlaceAndPin()
    {
        MiniWindowMemory.Remember(planner.Settings, "today", new MiniWindowState(new WindowPlacement(100, 120, 380, 560, false), Pinned: true));
        MiniWindowMemory.Remember(planner.Settings, "habits", new MiniWindowState(new WindowPlacement(700, 80, 320, 420, false), Pinned: false));
        MiniWindowMemory.Remember(planner.Settings, "today", new MiniWindowState(new WindowPlacement(140, 160, 400, 600, false), Pinned: false));

        var today = MiniWindowMemory.Of(planner.Settings, "today")!;
        var habits = MiniWindowMemory.Of(planner.Settings, "habits")!;
        Assert.Equal(new WindowPlacement(140, 160, 400, 600, false), today.Placement);
        Assert.False(today.Pinned);
        Assert.Equal(new WindowPlacement(700, 80, 320, 420, false), habits.Placement);
        Assert.Equal(2, planner.Settings.MiniWindows.Count);
    }

    [Fact]
    public void APlaceOnAMonitorThatIsGoneIsNotRestored()
    {
        var off = new WindowPlacement(-4000, -3000, 380, 560, false);
        var on = new WindowPlacement(120, 100, 380, 560, false);

        Assert.False(off.FitsWithin(0, 0, 1920, 1080));
        Assert.True(on.FitsWithin(0, 0, 1920, 1080));
    }

    private void Add(string line, DateOnly? day)
    {
        var draft = ComposerParser.Parse(line, planner.Time.GetLocalNow().DateTime) with { PlannedDate = day };
        Assert.NotNull(planner.Tasks.Add(draft));
        planner.Time.Advance(TimeSpan.FromSeconds(1));
    }

    private ListViewModel TodayList()
    {
        var composer = new ComposerViewModel(
            planner.Tasks, planner.Areas, planner.Tags, planner.Projects, planner.Settings, planner.Strings, planner.Time, _ => null, day => day, action => action());
        return new ListViewModel(
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
            action => action());
    }

    private HabitsViewModel Habits() => new(
        planner.Habits, planner.Goals, planner.Settings, planner.Strings, planner.Time, () => true, action => action());
}
