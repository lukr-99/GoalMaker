using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Tests.Sync;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>Goals on a real replica (docs/goals.md, M4-02).</summary>
public sealed class GoalListTests : IDisposable
{
    private readonly TestReplica test = new();
    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 9, 18, 12, 0, 0, TimeSpan.Zero));
    private readonly GoalList goals;
    private readonly TaskList tasks;

    public GoalListTests()
    {
        time.SetLocalTimeZone(TimeZoneInfo.Utc);
        var rows = new NewRows(test.Catalog, () => TestReplica.Owner, time);
        var areas = new AreaList(test.Replica, rows, ["violet"], () => { });
        goals = new GoalList(test.Replica, rows, () => { });
        tasks = new TaskList(test.Replica, rows, areas, new TagList(test.Replica, rows, () => { }), () => { }, () => new DateOnly(2026, 9, 18));
    }

    public void Dispose() => test.Dispose();

    [Fact]
    public void AGoalLandsOnItsPeriodsFirstDayAndKeepsOnlyAParentItCanServe()
    {
        var year = goals.Add(new GoalDraft("Run a half marathon", GoalHorizon.Year, new DateOnly(2026, 5, 2)))!;
        var lastYear = goals.Add(new GoalDraft("Old", GoalHorizon.Year, new DateOnly(2025, 3, 1)))!;

        var month = goals.Add(new GoalDraft("Run 80 km", GoalHorizon.Month, new DateOnly(2026, 9, 18), GoalRules.ModeNumber, ParentId: year.Id, Target: 80, Unit: "km"))!;
        var stray = goals.Add(new GoalDraft("3 runs", GoalHorizon.Week, new DateOnly(2026, 9, 18), ParentId: lastYear.Id))!;

        Assert.Equal(new DateOnly(2026, 1, 1), year.PeriodStart);
        Assert.Equal(new DateOnly(2026, 9, 1), month.PeriodStart);
        Assert.Equal(year.Id, month.ParentId);
        Assert.Equal(new DateOnly(2026, 9, 14), stray.PeriodStart);
        Assert.Null(stray.ParentId);
    }

    [Fact]
    public void ANumericGoalNeedsAPositiveTargetAndABlankTitleIsRefused()
    {
        Assert.Null(goals.Add(new GoalDraft("Run", GoalHorizon.Month, new DateOnly(2026, 9, 1), GoalRules.ModeNumber)));
        Assert.Null(goals.Add(new GoalDraft("Run", GoalHorizon.Month, new DateOnly(2026, 9, 1), GoalRules.ModeNumber, Target: 0)));
        Assert.Null(goals.Add(new GoalDraft("  ", GoalHorizon.Month, new DateOnly(2026, 9, 1))));
        Assert.Empty(goals.All());
    }

    [Fact]
    public void AmountsLinkedTasksAndTheDoneStatusDriveProgress()
    {
        var run = goals.Add(new GoalDraft("Run 20 km", GoalHorizon.Week, new DateOnly(2026, 9, 14), GoalRules.ModeNumber, Target: 20, Unit: "km"))!;
        Assert.NotNull(goals.LogAmount(run.Id, new DateOnly(2026, 9, 15), 5));
        Assert.NotNull(goals.LogAmount(run.Id, new DateOnly(2026, 9, 17), 10));
        Assert.Null(goals.LogAmount(run.Id, new DateOnly(2026, 9, 17), 0));
        var entries = goals.Entries().Where(entry => entry.GoalId == run.Id);
        Assert.Equal(0.75, GoalRules.Progress(run.Mode, run.Status, run.Target, [], entries).Fraction, 9);

        var fix = goals.Add(new GoalDraft("Fix the bike", GoalHorizon.Week, new DateOnly(2026, 9, 14), GoalRules.ModeTasks))!;
        var first = tasks.Add(Draft("Buy a chain"))!;
        var second = tasks.Add(Draft("Oil it"))!;
        tasks.SetGoal(first.Id, fix.Id);
        tasks.SetGoal(second.Id, fix.Id);
        tasks.SetDone(first.Id, true);
        var serving = tasks.All().Where(task => task.GoalId == fix.Id).ToList();
        Assert.Equal(0.5, GoalRules.Progress(fix.Mode, fix.Status, fix.Target, serving, []).Fraction, 9);

        Assert.True(goals.SetStatus(fix.Id, GoalRules.Done));
        var done = goals.Find(fix.Id)!;
        Assert.NotNull(done.CompletedAt);
        Assert.True(GoalRules.Progress(done.Mode, done.Status, done.Target, serving, []).Hit);
    }

    [Fact]
    public void LastWeeksGoalsAreCopiedOnceIntoAnEmptyWeek()
    {
        var month = goals.Add(new GoalDraft("Get fit", GoalHorizon.Month, new DateOnly(2026, 9, 1)))!;
        goals.Add(new GoalDraft("3 runs", GoalHorizon.Week, new DateOnly(2026, 9, 14), ParentId: month.Id));
        var read = goals.Add(new GoalDraft("Read", GoalHorizon.Week, new DateOnly(2026, 9, 14)))!;
        goals.SetStatus(read.Id, GoalRules.Dropped);

        Assert.Equal(1, goals.CopyPrevious(GoalHorizon.Week, new DateOnly(2026, 9, 21)));
        Assert.Equal(0, goals.CopyPrevious(GoalHorizon.Week, new DateOnly(2026, 9, 21)));

        var copied = goals.All().Single(goal => goal.PeriodStart == new DateOnly(2026, 9, 21));
        Assert.Equal(("3 runs", month.Id, GoalRules.Open), (copied.Title, copied.ParentId, copied.Status));
    }

    [Fact]
    public void ADeletedGoalLeavesTheList()
    {
        var goal = goals.Add(new GoalDraft("Read", GoalHorizon.Day, new DateOnly(2026, 9, 18)))!;

        Assert.True(goals.Delete(goal.Id));

        Assert.Empty(goals.All());
        Assert.False(goals.Update(goal.Id, new GoalDraft("Read more", GoalHorizon.Day, new DateOnly(2026, 9, 18))));
    }

    [Fact]
    public void ARepeatingTasksNextOccurrenceKeepsItsGoalOnlyInsideTheGoalsPeriod()
    {
        var week = goals.Add(new GoalDraft("3 runs", GoalHorizon.Week, new DateOnly(2026, 9, 14), GoalRules.ModeTasks))!;
        var friday = tasks.Add(Draft("Run daily"))!;
        tasks.SetGoal(friday.Id, week.Id);

        tasks.SetDone(friday.Id, true);
        var saturday = tasks.All().Single(task => task.State == TaskState.Open);
        tasks.SetDone(saturday.Id, true);
        var sunday = tasks.All().Single(task => task.State == TaskState.Open);
        tasks.SetDone(sunday.Id, true);
        var monday = tasks.All().Single(task => task.State == TaskState.Open);

        Assert.Equal(((DateOnly?)new DateOnly(2026, 9, 19), week.Id), (saturday.PlannedDate, saturday.GoalId));
        Assert.Equal(((DateOnly?)new DateOnly(2026, 9, 20), week.Id), (sunday.PlannedDate, sunday.GoalId));
        Assert.Equal(((DateOnly?)new DateOnly(2026, 9, 21), (string?)null), (monday.PlannedDate, monday.GoalId));
    }

    private static ComposerDraft Draft(string line) => ComposerParser.Parse(line, new DateTime(2026, 9, 18, 8, 0, 0));
}
