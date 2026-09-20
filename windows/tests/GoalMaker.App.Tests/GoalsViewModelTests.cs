using System.Globalization;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>The Windows Goals page over a real replica: periods, progress, the cascade, the editor and logging (M4-02).</summary>
public sealed class GoalsViewModelTests : IDisposable
{
    private readonly TestPlanner planner = new();
    private bool motionReduced;

    public void Dispose() => planner.Dispose();

    [Fact]
    public void TheSectionsAreThisYearMonthWeekAndDayThenNextWeek()
    {
        var page = Page();

        Assert.Equal(
            [
                (GoalHorizon.Year, new DateOnly(2026, 1, 1)),
                (GoalHorizon.Month, new DateOnly(2026, 9, 1)),
                (GoalHorizon.Week, new DateOnly(2026, 9, 14)),
                (GoalHorizon.Day, new DateOnly(2026, 9, 18)),
                (GoalHorizon.Week, new DateOnly(2026, 9, 21)),
            ],
            page.Sections.Select(section => (section.Horizon, section.Start)));
        var week = new DateOnly(2026, 9, 14);
        var range = $"GOALS.RANGE({week:%d},{week.AddDays(6).ToString("d MMM", CultureInfo.CurrentCulture)})".ToUpperInvariant();
        Assert.Equal($"GOALS.SECTION(GOALS.THISWEEK,{range})", page.Sections[2].Header);
    }

    [Fact]
    public void RowsSayWhereEachGoalStands()
    {
        var runs = planner.Goals.Add(new GoalDraft("3 runs", GoalHorizon.Week, new DateOnly(2026, 9, 14), GoalRules.ModeTasks))!;
        planner.Goals.Add(new GoalDraft("Run 80 km", GoalHorizon.Month, new DateOnly(2026, 9, 1), GoalRules.ModeNumber, Target: 80, Unit: "km"));
        var first = Add("Morning run");
        Add("Long run");
        planner.Tasks.SetGoal(first.Id, runs.Id);
        planner.Tasks.SetGoal(planner.Task("Long run").Id, runs.Id);
        planner.Tasks.SetDone(first.Id, true);
        var page = Page();

        var week = page.Sections[2].Rows.Single();
        var month = page.Sections[1].Rows.Single();
        Assert.Equal(("Goals.TasksDone(1,2)", 0.5, 0.5.ToString("P0", CultureInfo.CurrentCulture)), (week.ProgressText, week.Fraction, week.RingText));
        Assert.Equal("Goals.AmountUnit(0,80,km)", month.ProgressText);
        Assert.True(month.CanLog);
    }

    [Fact]
    public void LoggingAnAmountCountsTowardTheGoalAndTakeOffCorrects()
    {
        planner.Goals.Add(new GoalDraft("Run 20 km", GoalHorizon.Week, new DateOnly(2026, 9, 14), GoalRules.ModeNumber, Target: 20, Unit: "km"));
        var page = Page();

        page.Sections[2].Rows.Single().LogCommand.Execute(null);
        Assert.True(page.IsLogging);
        Assert.Equal(("Goals.LogTitle(Run 20 km)", "km"), (page.LogTitle, page.LogUnit));
        page.LogText = "12,5";
        page.AddAmountCommand.Execute(null);
        page.Sections[2].Rows.Single().LogCommand.Execute(null);
        page.LogText = "2.5";
        page.TakeOffAmountCommand.Execute(null);

        Assert.False(page.IsLogging);
        Assert.Equal("Goals.AmountUnit(10,20,km)", page.Sections[2].Rows.Single().ProgressText);
        Assert.Equal(new DateOnly(2026, 9, 18), planner.Goals.Entries().First().Day);
    }

    [Fact]
    public void TheEditorAddsAGoalInTheSectionsPeriodAndRefusesABlankOne()
    {
        var page = Page();
        page.Sections[4].AddCommand.Execute(null);
        var editor = page.Editor;
        Assert.True(editor.IsOpen);
        Assert.Equal(("week", "2026-09-21"), (editor.Horizon!.Id, editor.Period!.Id));

        editor.SaveCommand.Execute(null);
        Assert.True(editor.HasError);

        editor.Title = "Plan the trip";
        editor.Mode = editor.Modes.Single(mode => mode.Id == GoalRules.ModeNumber);
        editor.SaveCommand.Execute(null);
        Assert.True(editor.HasError);

        editor.TargetText = "3";
        editor.Unit = "calls";
        editor.SaveCommand.Execute(null);

        Assert.False(editor.IsOpen);
        var goal = planner.Goals.All().Single();
        Assert.Equal(("Plan the trip", new DateOnly(2026, 9, 21), 3.0, "calls"), (goal.Title, goal.PeriodStart, goal.Target!.Value, goal.Unit));
        Assert.Equal("Plan the trip", page.Sections[4].Rows.Single().Title);
    }

    [Fact]
    public void TheEditorOffersOnlyGoalsTheNewOneCanServe()
    {
        var year = planner.Goals.Add(new GoalDraft("Half marathon", GoalHorizon.Year, new DateOnly(2026, 1, 1)))!;
        planner.Goals.Add(new GoalDraft("Last year", GoalHorizon.Year, new DateOnly(2025, 1, 1)));
        planner.Goals.Add(new GoalDraft("Another week", GoalHorizon.Week, new DateOnly(2026, 9, 14)));
        var page = Page();

        page.Sections[2].AddCommand.Execute(null);
        var editor = page.Editor;
        Assert.Equal([null, year.Id], editor.Parents.Select(choice => choice.Id));

        editor.Title = "3 runs";
        editor.Parent = editor.Parents[1];
        editor.SaveCommand.Execute(null);

        Assert.Equal(year.Id, planner.Goals.All().Single(goal => goal.Title == "3 runs").ParentId);
        Assert.Equal([("Half marathon", 0), ("3 runs", 1), ("Another week", 0)], page.Tree.Select(row => (row.Title, row.Depth)));
    }

    [Fact]
    public void EditingKeepsTheGoalsOwnPeriodOnOffer()
    {
        var old = planner.Goals.Add(new GoalDraft("Tidy up", GoalHorizon.Week, new DateOnly(2026, 8, 31)))!;
        var page = Page();

        page.Edit(old);

        Assert.Equal("2026-08-31", page.Editor.Period!.Id);
        Assert.Contains(page.Editor.Periods, choice => choice.Id == "2026-09-14");
        Assert.True(page.Editor.CanDelete);
        page.Editor.DeleteCommand.Execute(null);
        Assert.Empty(planner.Goals.All());
    }

    [Fact]
    public void ANewHitCelebratesUnlessMotionIsReduced()
    {
        var goal = planner.Goals.Add(new GoalDraft("Book the race", GoalHorizon.Week, new DateOnly(2026, 9, 14)))!;
        var other = planner.Goals.Add(new GoalDraft("Buy shoes", GoalHorizon.Week, new DateOnly(2026, 9, 14)))!;
        var page = Page();
        var bursts = 0;
        page.Celebrate += (_, _) => bursts++;

        page.Sections[2].Rows.Single(row => row.Id == goal.Id).IsDone = true;
        Assert.Equal(1, bursts);

        motionReduced = true;
        page.Sections[2].Rows.Single(row => row.Id == other.Id).IsDone = true;
        Assert.Equal(1, bursts);
    }

    [Fact]
    public void AnEmptyWeekOffersLastWeeksGoals()
    {
        planner.Goals.Add(new GoalDraft("3 runs", GoalHorizon.Week, new DateOnly(2026, 9, 7)));
        var page = Page();

        Assert.True(page.Sections[2].CanCopy);
        Assert.Equal("Goals.CopyWeek", page.Sections[2].CopyLabel);
        page.Sections[2].CopyCommand.Execute(null);

        Assert.Equal("3 runs", page.Sections[2].Rows.Single().Title);
        Assert.False(page.Sections[2].CanCopy);
    }

    private GoalsViewModel Page() => new(planner.Goals, planner.Tasks, planner.Settings, planner.Strings, planner.Time, () => motionReduced, action => action());

    private TaskItem Add(string line)
    {
        var task = planner.Tasks.Add(ComposerParser.Parse(line, planner.Time.GetLocalNow().DateTime))!;
        planner.Time.Advance(TimeSpan.FromSeconds(1));
        return task;
    }
}
