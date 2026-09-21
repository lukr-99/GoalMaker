using GoalMaker.App.ViewModels;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>The Windows Habits page over a real replica: rings, streaks, the editor, skipping and pausing (M4-04).</summary>
public sealed class HabitsViewModelTests : IDisposable
{
    private static readonly DateOnly Today = new(2026, 9, 18);
    private readonly TestPlanner planner = new();
    private bool motionReduced;

    public void Dispose() => planner.Dispose();

    [Fact]
    public void RowsSayHowOftenAHabitRunsAndWhereTodayStands()
    {
        planner.Habits.Add(new HabitDraft("Read", Today));
        planner.Habits.Add(new HabitDraft("Water", Today) { Measure = HabitRules.Count, Target = 8, Unit = "glasses" });
        planner.Habits.Add(new HabitDraft("Gym", Today) { Cadence = HabitRules.OnWeekdays, Weekdays = 5 });
        planner.Habits.Add(new HabitDraft("Run", Today) { Cadence = HabitRules.PerWeek, Times = 3 });
        var page = Page();

        Assert.Equal(
            [
                ("Habits.CadenceDaily", "Habits.NotYet"),
                ("Habits.CadenceDaily", "Habits.ValueUnit(0,8,glasses)"),
                ("Habits.TimesWeek(3)", "Habits.MetWeek(0,3)"),
                ("Mon, Wed", "Habits.NotDue"),
            ],
            page.Rows.Select(row => (row.CadenceText, row.StatusText)).OrderBy(row => row.Item1).ThenBy(row => row.Item2));
    }

    [Fact]
    public void ACheckInFillsTheRingAndTheStreakCounts()
    {
        var read = planner.Habits.Add(new HabitDraft("Read", Today.AddDays(-10)))!;
        planner.Habits.CheckIn(read.Id, Today.AddDays(-2));
        planner.Habits.CheckIn(read.Id, Today.AddDays(-1));
        var page = Page();

        page.Rows.Single().CheckInCommand.Execute(null);

        var row = page.Rows.Single();
        Assert.Equal((1.0, 3, "Habits.StreakDays(3)"), (row.Fraction, row.Streak, row.StreakText));
        Assert.True(row.ShowsCheck);
        Assert.Equal(Today, planner.Habits.Checkins().Max(checkin => checkin.Day));
    }

    [Fact]
    public void TappingAnAmountHabitOpensTheLogPanel()
    {
        planner.Habits.Add(new HabitDraft("Run", Today) { Measure = HabitRules.Amount, Target = 5, Unit = "km" });
        var page = Page();
        var asked = 0;
        page.LogRequested += (_, _) => asked++;

        page.Rows.Single().CheckInCommand.Execute(null);

        Assert.True(page.IsLogging);
        Assert.Equal(("Habits.LogTitle(Run)", "km", 1), (page.LogTitle, page.LogUnit, asked));
        page.LogText = "2,5";
        page.AddAmountCommand.Execute(null);

        Assert.False(page.IsLogging);
        Assert.Equal(0.5, page.Rows.Single().Fraction, 9);
    }

    [Fact]
    public void TheEditorAddsAHabitAndRefusesOneWithoutADayOrATarget()
    {
        var page = Page();
        page.NewHabitCommand.Execute(null);
        var editor = page.Editor;
        Assert.True(editor.IsOpen);

        editor.SaveCommand.Execute(null);
        Assert.True(editor.HasError);

        editor.Name = "Gym";
        editor.Cadence = editor.Cadences.Single(choice => choice.Id == HabitRules.OnWeekdays);
        foreach (var day in editor.Days)
        {
            day.IsChosen = false;
        }

        editor.SaveCommand.Execute(null);
        Assert.True(editor.HasError);

        editor.Days[0].IsChosen = true;
        editor.Days[2].IsChosen = true;
        editor.Measure = editor.Measures.Single(choice => choice.Id == HabitRules.Count);
        editor.SaveCommand.Execute(null);
        Assert.True(editor.HasError);

        editor.TargetText = "3";
        editor.Unit = "sets";
        editor.SaveCommand.Execute(null);

        Assert.False(editor.IsOpen);
        var habit = planner.Habits.All().Single();
        Assert.Equal(("Gym", HabitRules.OnWeekdays, 5, 3.0, "sets", Today), (habit.Name, habit.Cadence, habit.Weekdays!.Value, habit.Target!.Value, habit.Unit, habit.StartsOn));
    }

    [Fact]
    public void TheEditorCountsTimesAWeekAndOffersTheGoalsAHabitCanServe()
    {
        var goal = planner.Goals.Add(new GoalDraft("Run 80 km", GoalHorizon.Month, Today, GoalRules.ModeNumber, Target: 80, Unit: "km"))!;
        planner.Goals.Add(new GoalDraft("Over", GoalHorizon.Week, Today.AddDays(-14)));
        var page = Page();
        page.NewHabitCommand.Execute(null);
        var editor = page.Editor;

        editor.Name = "Run";
        editor.Cadence = editor.Cadences.Single(choice => choice.Id == HabitRules.PerWeek);
        editor.MoreTimesCommand.Execute(null);
        editor.FewerTimesCommand.Execute(null);
        editor.FewerTimesCommand.Execute(null);
        Assert.Equal("Habits.TimesWeek(2)", editor.TimesLabel);
        Assert.Equal([null, goal.Id], editor.GoalChoices.Select(choice => choice.Id));

        editor.Goal = editor.GoalChoices[1];
        editor.SaveCommand.Execute(null);

        var habit = planner.Habits.All().Single();
        Assert.Equal((HabitRules.PerWeek, 2, goal.Id), (habit.Cadence, habit.Times!.Value, habit.GoalId));
        Assert.Equal("Habits.Serves(Run 80 km)", page.Rows.Single().Serves);
    }

    [Fact]
    public void SkippingAndPausingKeepTheStreakAndShowInTheRow()
    {
        var read = planner.Habits.Add(new HabitDraft("Read", Today.AddDays(-10)))!;
        planner.Habits.CheckIn(read.Id, Today.AddDays(-1));
        var page = Page();

        page.Rows.Single().SkipCommand.Execute(null);
        Assert.Equal(("Habits.Skipped", 1, true), (page.Rows.Single().StatusText, page.Rows.Single().Streak, page.Rows.Single().IsDone));

        page.Rows.Single().UnskipCommand.Execute(null);
        page.Rows.Single().PauseCommand.Execute(null);
        Assert.Equal(("Habits.Paused", false), (page.Rows.Single().StatusText, page.Rows.Single().CanCheckIn));
        Assert.Empty(page.TodayRows());

        page.Rows.Single().ResumeCommand.Execute(null);
        Assert.Equal("Habits.NotYet", page.Rows.Single().StatusText);
        Assert.Single(page.TodayRows());
    }

    [Fact]
    public void ArchivingMovesAHabitToItsOwnListAndDeletingRemovesIt()
    {
        planner.Habits.Add(new HabitDraft("Read", Today));
        var page = Page();

        page.Rows.Single().ArchiveCommand.Execute(null);
        Assert.Empty(page.Rows);
        Assert.Equal(("Read", "HABITS.ARCHIVED(1)", true), (page.Archived.Single().Name, page.ArchivedHeader, page.HasArchived));
        Assert.Empty(page.TodayRows());

        page.Archived.Single().UnarchiveCommand.Execute(null);
        page.Rows.Single().DeleteCommand.Execute(null);
        Assert.True(page.IsEmpty);
    }

    [Fact]
    public void TodaysRowsHoldTheHabitsDueTodayWithNoHeatmap()
    {
        planner.Habits.Add(new HabitDraft("Read", Today));
        // Friday the 18th is outside Monday and Wednesday (mask 5).
        planner.Habits.Add(new HabitDraft("Gym", Today) { Cadence = HabitRules.OnWeekdays, Weekdays = 5 });
        planner.Habits.Add(new HabitDraft("Run", Today) { Cadence = HabitRules.PerWeek, Times = 3 });
        var page = Page();

        Assert.Equal(["Read", "Run"], page.TodayRows().Select(row => row.Name).Order());
        Assert.Empty(page.TodayRows()[0].Heat);
        Assert.NotEmpty(page.Rows[0].Heat);
    }

    [Fact]
    public void AMilestoneStreakCelebratesOnce()
    {
        var read = planner.Habits.Add(new HabitDraft("Read", Today.AddDays(-30)))!;
        foreach (var back in Enumerable.Range(1, 6))
        {
            planner.Habits.CheckIn(read.Id, Today.AddDays(-back));
        }

        var page = Page();
        var parties = 0;
        page.Celebrate += (_, _) => parties++;

        page.Rows.Single().CheckInCommand.Execute(null);

        Assert.Equal((7, 1), (page.Rows.Single().Streak, parties));
        page.Refresh();
        Assert.Equal(1, parties);
    }

    [Fact]
    public void NoConfettiWhenMotionIsReduced()
    {
        var read = planner.Habits.Add(new HabitDraft("Read", Today.AddDays(-30)))!;
        foreach (var back in Enumerable.Range(1, 6))
        {
            planner.Habits.CheckIn(read.Id, Today.AddDays(-back));
        }

        motionReduced = true;
        var page = Page();
        var parties = 0;
        page.Celebrate += (_, _) => parties++;

        page.Rows.Single().CheckInCommand.Execute(null);

        Assert.Equal(0, parties);
    }

    [Fact]
    public void ALimitKeepsTheStreakUntilTheDayGoesOverIt()
    {
        var snacks = planner.Habits.Add(new HabitDraft("Snacks", Today.AddDays(-3))
        {
            Measure = HabitRules.Count,
            Target = 2,
            Unit = "snacks",
            Direction = HabitRules.AtMost,
        })!;
        var page = Page();

        // Three days nobody logged anything on are three days kept.
        var row = page.Rows.Single();
        Assert.Equal(3, row.Streak);
        Assert.False(row.IsOver);
        Assert.Equal("Habits.LimitUnit(0,2,snacks)", row.StatusText);

        planner.Habits.CheckIn(snacks.Id, Today, 2);
        row = Page().Rows.Single();
        Assert.False(row.IsOver);
        Assert.Equal(1, row.Fraction);
        Assert.False(row.IsDone);
        Assert.Equal(3, row.Streak);

        planner.Habits.CheckIn(snacks.Id, Today, 1);
        row = Page().Rows.Single();
        Assert.True(row.IsOver);
        Assert.Equal("Habits.LimitUnit(3,2,snacks)", row.StatusText);
        Assert.Equal(0, row.Streak);
    }

    [Fact]
    public void TheEditorSavesALimitAndOnlyOffersItOnDays()
    {
        var page = Page();
        page.NewHabitCommand.Execute(null);
        var editor = page.Editor;
        editor.Name = "Snacks";
        editor.Measure = editor.Measures.Single(choice => choice.Id == HabitRules.Count);
        editor.Direction = editor.Directions.Single(choice => choice.Id == HabitRules.AtMost);
        editor.TargetText = "2";
        Assert.True(editor.IsLimit);
        Assert.True(editor.HasLimitHint);

        // A week cannot hold a limit, so the choice goes away and the habit is one to build again.
        editor.Cadence = editor.Cadences.Single(choice => choice.Id == HabitRules.PerWeek);
        Assert.False(editor.CanBeLimit);
        Assert.False(editor.IsLimit);

        editor.Cadence = editor.Cadences.Single(choice => choice.Id == HabitRules.Daily);
        editor.SaveCommand.Execute(null);

        Assert.False(editor.IsOpen);
        var habit = planner.Habits.All().Single();
        Assert.Equal((HabitRules.AtMost, 2.0), (habit.Direction, habit.Target!.Value));

        page.Edit(habit);
        Assert.Equal(HabitRules.AtMost, editor.Direction!.Id);
    }

    [Fact]
    public void AnAmountIsLoggedFromTheRowItself()
    {
        var water = planner.Habits.Add(new HabitDraft("Water", Today)
        {
            Measure = HabitRules.Count,
            Target = 8,
            Unit = "glasses",
        })!;
        var page = Page();
        var row = page.Rows.Single();
        Assert.True(row.CanLog);
        Assert.Equal("glasses", row.AmountHint);

        row.LogOneCommand.Execute(null);

        row = Page().Rows.Single();
        Assert.Equal(1, row.Value);
        Assert.Equal(0.125, row.Fraction, 9);

        // The same check-in the panel writes: one row for the habit and the day, added to.
        row.AmountText = "3";
        row.LogAmountCommand.Execute(null);

        row = Page().Rows.Single();
        Assert.Equal(4, row.Value);
        Assert.Equal(0.5, row.Fraction, 9);
        Assert.Equal(water.Id, Assert.Single(planner.Habits.Checkins()).HabitId);
    }

    [Fact]
    public void WhatIsNotANumberLeavesTheDayAlone()
    {
        planner.Habits.Add(new HabitDraft("Water", Today) { Measure = HabitRules.Count, Target = 8, Unit = "glasses" });
        var page = Page();
        var row = page.Rows.Single();

        row.AmountText = "a few";
        row.LogAmountCommand.Execute(null);

        Assert.Equal(0, Page().Rows.Single().Value);
    }

    [Fact]
    public void ACheckHabitHasNothingToLogFromTheRow()
    {
        planner.Habits.Add(new HabitDraft("Read", Today));

        Assert.False(Page().Rows.Single().CanLog);
    }

    private HabitsViewModel Page() =>
        new(planner.Habits, planner.Goals, planner.Settings, planner.Strings, planner.Time, () => motionReduced, action => action());
}
