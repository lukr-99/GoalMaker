using GoalMaker.App.Startup;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>Plan tomorrow as the Windows app runs it (docs/plan-tomorrow.md), over a real replica and without a window.</summary>
public sealed class PlanViewModelTests : IDisposable
{
    private static readonly DateOnly Today = new(2026, 9, 18);
    private static readonly DateOnly Tomorrow = new(2026, 9, 19);
    private readonly TestPlanner planner = new();
    private readonly List<AppPage> opened = [];
    private PlanViewModel? plan;

    public void Dispose() => planner.Dispose();

    [Fact]
    public void StepOneAsksAboutTodayAndOverdueUntilEverythingIsDecided()
    {
        Add("Call the bank", Today);
        Add("File receipts", new DateOnly(2026, 9, 16));
        Add("Pack bags", Tomorrow);
        Add("Someday idea", null);
        var ritual = Plan();

        Assert.Equal(["File receipts", "Call the bank"], ritual.Review.Select(row => row.Title));
        Assert.Equal(2, ritual.Undecided);
        Assert.NotEmpty(ritual.Review[0].DayText);
        Assert.False(ritual.NextCommand.CanExecute(null));

        ritual.Review[0].ChooseTomorrowCommand.Execute(null);
        ritual.Review[1].ChooseDropCommand.Execute(null);

        Assert.Equal(Tomorrow, planner.Task("File receipts").PlannedDate);
        Assert.Equal(TaskState.Dropped, planner.Task("Call the bank").State);
        Assert.Equal(["File receipts", "Call the bank"], ritual.Review.Select(row => row.Title));
        Assert.True(ritual.Review[0].IsTomorrow);
        Assert.True(ritual.Review[1].IsDropped);
        Assert.Equal("Plan.StepTodayDone", ritual.StepText);
        Assert.True(ritual.NextCommand.CanExecute(null));

        ritual.NextCommand.Execute(null);
        Assert.True(ritual.IsTomorrowStep);
        Assert.Equal(["File receipts", "Pack bags"], ritual.Tomorrow.Select(row => row.Title));
    }

    [Fact]
    public void ADecisionCanBeChangedUntilTheEnd()
    {
        Add("Tax return", Today);
        var ritual = Plan();
        var row = ritual.Review.Single();

        row.PickDateCommand.Execute(null);
        Assert.True(row.IsPicking);
        row.PickedDate = new DateTime(2026, 9, 25);
        Assert.False(row.IsPicking);
        Assert.True(row.IsLater);
        Assert.Equal(new DateOnly(2026, 9, 25), planner.Task("Tax return").PlannedDate);
        Assert.NotEqual("Plan.Date", row.DateText);

        row.ChooseDoneCommand.Execute(null);
        Assert.True(row.IsDone);
        Assert.Equal(TaskState.Done, planner.Task("Tax return").State);

        row.ChooseTomorrowCommand.Execute(null);
        Assert.True(row.IsTomorrow);
        Assert.Equal(TaskState.Open, planner.Task("Tax return").State);
        Assert.Equal(Tomorrow, planner.Task("Tax return").PlannedDate);
        Assert.Same(row, ritual.Review.Single());
    }

    [Fact]
    public void PickingTodayOrEarlierIsIgnored()
    {
        Add("Tax return", Today);
        var ritual = Plan();

        ritual.Review.Single().PickedDate = new DateTime(2026, 9, 18);

        Assert.Equal(Today, planner.Task("Tax return").PlannedDate);
        Assert.Equal(1, ritual.Undecided);
    }

    [Fact]
    public void TomorrowStopsAtThreeTopPriorities()
    {
        foreach (var title in new[] { "One", "Two", "Three", "Four" })
        {
            Add(title, Tomorrow);
        }

        var ritual = Plan();
        ritual.NextCommand.Execute(null);
        var rows = ritual.Tomorrow.ToList();

        rows[0].ToggleCommand.Execute(null);
        rows[1].ToggleCommand.Execute(null);
        rows[2].ToggleCommand.Execute(null);
        Assert.False(rows[3].CanToggle);
        rows[3].ToggleCommand.Execute(null);

        Assert.False(planner.Task("Four").TopPriority);
        Assert.Equal("Plan.StepTomorrow(3,3)", ritual.StepText);
        Assert.Equal(rows, ritual.Tomorrow);

        rows[0].ToggleCommand.Execute(null);
        Assert.True(rows[3].CanToggle);
        Assert.False(planner.Task("One").TopPriority);
    }

    [Fact]
    public void TheInboxAndTheComposerFillTomorrow()
    {
        Add("Read about sourdough", null);
        var ritual = Plan();
        ritual.NextCommand.Execute(null);
        Assert.True(ritual.HasInbox);

        ritual.Inbox.Single().PlanTomorrowCommand.Execute(null);
        ritual.Composer.NewTaskTitle = "Gym bag";
        ritual.Composer.AddTaskCommand.Execute(null);

        Assert.Empty(ritual.Inbox);
        Assert.False(ritual.HasInbox);
        Assert.Equal(Tomorrow, planner.Task("Read about sourdough").PlannedDate);
        Assert.Equal(Tomorrow, planner.Task("Gym bag").PlannedDate);
        Assert.Equal(2, ritual.Tomorrow.Count);
    }

    [Fact]
    public void FinishingSumsUpTheEvening()
    {
        Add("Call the bank", Today);
        Add("File receipts", Today);
        Add("Pack bags !", Tomorrow);
        var ritual = Plan();
        ritual.Review[0].ChooseTomorrowCommand.Execute(null);
        ritual.Review[1].ChooseDropCommand.Execute(null);
        ritual.NextCommand.Execute(null);
        ritual.NextCommand.Execute(null);

        Assert.True(ritual.IsDoneStep);
        Assert.Equal("2", ritual.TomorrowCount);
        Assert.Equal("Plan.DoneTasks", ritual.TomorrowCountLabel);
        Assert.Equal("Plan.DonePriority(1)", ritual.PrioritiesText);
        Assert.Equal("Plan.Outcome(Plan.OutcomeTomorrow(1) · Plan.OutcomeDropped(1))", ritual.OutcomeText);

        ritual.CloseCommand.Execute(null);
        Assert.Equal([AppPage.Today], opened);
    }

    [Fact]
    public void SlashPlanStartsTheRitualOver()
    {
        Add("Call the bank", Today);
        var ritual = Plan();
        ritual.Review.Single().ChooseTomorrowCommand.Execute(null);
        ritual.NextCommand.Execute(null);

        ritual.Composer.NewTaskTitle = "/plan";
        Assert.True(ritual.Composer.AddTaskCommand.CanExecute(null));
        ritual.Composer.AddTaskCommand.Execute(null);

        Assert.True(ritual.IsTodayStep);
        Assert.Empty(ritual.Review);
        Assert.Equal("Plan.TodayClear", ritual.TodayIntro);
        Assert.Equal(string.Empty, ritual.Composer.NewTaskTitle);
    }

    [Fact]
    public void TasksDueTodayElsewhereJoinAtTheEnd()
    {
        Add("First", Today);
        var ritual = Plan();
        Add("From the phone", Today);

        Assert.Equal(["First", "From the phone"], ritual.Review.Select(row => row.Title));
        Assert.Equal(2, ritual.Undecided);
    }

    private PlanViewModel Plan()
    {
        var composer = new ComposerViewModel(
            planner.Tasks,
            planner.Areas,
            planner.Tags,
            planner.Settings,
            planner.Strings,
            planner.Time,
            _ => null,
            day => day.AddDays(1),
            action => action(),
            () => plan?.Start());
        plan = new PlanViewModel(
            planner.Tasks, planner.Areas, composer, planner.Settings, planner.Strings, planner.Time, _ => null, planner.Tick, opened.Add, action => action());
        return plan;
    }

    private void Add(string line, DateOnly? day)
    {
        var draft = ComposerParser.Parse(line, planner.Time.GetLocalNow().DateTime) with { PlannedDate = day };
        Assert.NotNull(planner.Tasks.Add(draft));
        planner.Time.Advance(TimeSpan.FromSeconds(1));
    }
}
