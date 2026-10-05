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
        // A skip is neither done nor left (contracts/vectors/habits.json, standings): Hide done keeps it in place.
        Assert.Equal(("Habits.Skipped", 1, HabitStanding.Skipped), (page.Rows.Single().StatusText, page.Rows.Single().Streak, page.Rows.Single().Standing));
        Assert.False(page.Rows.Single().IsDone);
        Assert.False(page.Rows.Single().IsLeft);

        page.Rows.Single().UnskipCommand.Execute(null);
        page.Rows.Single().PauseCommand.Execute(null);
        Assert.Equal(("Habits.Paused", false), (page.Rows.Single().StatusText, page.Rows.Single().CanCheckIn));
        Assert.Empty(page.TodayRows());

        page.Rows.Single().ResumeCommand.Execute(null);
        Assert.Equal("Habits.NotYet", page.Rows.Single().StatusText);
        Assert.Single(page.TodayRows());
    }

    [Fact]
    public void TheFormSetsAReminderTimeAndRefusesOneThatIsNotATime()
    {
        var page = Page();
        page.NewHabitCommand.Execute(null);
        Assert.False(page.Editor.RemindOn);
        page.Editor.Name = "Stretch";
        page.Editor.RemindOn = true;
        page.Editor.RemindText = "25:00";
        page.Editor.SaveCommand.Execute(null);
        Assert.Empty(planner.Habits.All());

        page.Editor.RemindText = "21:15";
        page.Editor.SaveCommand.Execute(null);
        Assert.Equal(new TimeOnly(21, 15), planner.Habits.All().Single().RemindAt);

        page.Editor.OpenEdit(planner.Habits.All().Single());
        Assert.Equal((true, "21:15"), (page.Editor.RemindOn, page.Editor.RemindText));
        page.Editor.RemindOn = false;
        page.Editor.SaveCommand.Execute(null);
        Assert.Null(planner.Habits.All().Single().RemindAt);
    }

    [Fact]
    public void AHabitKeptOffTodayStaysOnThePageAndIsStillDue()
    {
        planner.Habits.Add(new HabitDraft("Read", Today));
        var page = Page();
        page.NewHabitCommand.Execute(null);
        Assert.True(page.Editor.ShowOnToday);
        page.Editor.Name = "Floss";
        page.Editor.ShowOnToday = false;
        page.Editor.SaveCommand.Execute(null);

        var floss = page.Rows.Single(row => row.Name == "Floss");
        Assert.False(planner.Habits.All().Single(habit => habit.Name == "Floss").ShowOnToday);
        Assert.True(floss.IsOffToday);
        Assert.Equal(["Read"], page.TodayRows().Select(row => row.Name));
        Assert.Equal(["Read", "Floss"], page.DueRows().Select(row => row.Name));

        floss.CheckInCommand.Execute(null);
        Assert.True(page.DueRows().Single(row => row.Name == "Floss").IsDone);

        page.Editor.OpenEdit(floss.Habit);
        Assert.False(page.Editor.ShowOnToday);
        page.Editor.ShowOnToday = true;
        page.Editor.SaveCommand.Execute(null);
        Assert.Equal(["Read", "Floss"], page.TodayRows().Select(row => row.Name));
        Assert.False(page.Rows.Single(row => row.Name == "Floss").IsOffToday);
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
    public void TheEditorSavesALimitOnAnyCadence()
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
        Assert.Equal("Habits.TargetMostDay", editor.TargetLabel);

        // A week keeps the limit; a count's target is then the week's total, so the days are not asked.
        editor.Cadence = editor.Cadences.Single(choice => choice.Id == HabitRules.PerWeek);
        Assert.True(editor.IsLimit);
        Assert.False(editor.IsTimes);
        Assert.Equal(("Habits.LimitHintWeek", "Habits.TargetMostWeek"), (editor.LimitHint, editor.TargetLabel));

        editor.Cadence = editor.Cadences.Single(choice => choice.Id == HabitRules.Daily);
        editor.SaveCommand.Execute(null);

        Assert.False(editor.IsOpen);
        var habit = planner.Habits.All().Single();
        Assert.Equal((HabitRules.AtMost, 2.0), (habit.Direction, habit.Target!.Value));

        page.Edit(habit);
        Assert.Equal(HabitRules.AtMost, editor.Direction!.Id);
    }

    [Fact]
    public void TheEditorSavesAWeeklyCheckLimitThatCanGoDownToZero()
    {
        var page = Page();
        page.NewHabitCommand.Execute(null);
        var editor = page.Editor;
        editor.Name = "Smoke";
        editor.Cadence = editor.Cadences.Single(choice => choice.Id == HabitRules.PerWeek);
        editor.Direction = editor.Directions.Single(choice => choice.Id == HabitRules.AtMost);
        Assert.True(editor.IsTimes);
        Assert.Equal("Habits.LimitHintWeekCheck", editor.LimitHint);

        for (var press = 0; press < 5; press++)
        {
            editor.FewerTimesCommand.Execute(null);
        }

        Assert.Equal((0, "Habits.AtMostWeek(0)"), (editor.Times, editor.TimesLabel));

        // A habit to build needs at least one day, so going back to one lifts the stepper to 1.
        editor.Direction = editor.Directions.Single(choice => choice.Id == HabitRules.AtLeast);
        Assert.Equal((1, "Habits.TimesWeek(1)"), (editor.Times, editor.TimesLabel));
        editor.Direction = editor.Directions.Single(choice => choice.Id == HabitRules.AtMost);
        editor.FewerTimesCommand.Execute(null);
        editor.SaveCommand.Execute(null);

        Assert.False(editor.IsOpen);
        var habit = planner.Habits.All().Single();
        Assert.Equal((HabitRules.PerWeek, 0, HabitRules.AtMost), (habit.Cadence, habit.Times, habit.Direction));
        Assert.Equal("Habits.NoneWeek", Page().Rows.Single().StatusText);
    }

    [Fact]
    public void TheEditorSavesAMonthlyCountLimitWithOneDayAndRefusesABuildWithZero()
    {
        var page = Page();
        page.NewHabitCommand.Execute(null);
        var editor = page.Editor;
        editor.Name = "Drinks";
        editor.Cadence = editor.Cadences.Single(choice => choice.Id == HabitRules.PerMonth);
        editor.Measure = editor.Measures.Single(choice => choice.Id == HabitRules.Count);
        editor.TargetText = "0";

        // Something to reach with a target of 0 says nothing to do.
        editor.SaveCommand.Execute(null);
        Assert.True(editor.IsOpen);
        Assert.True(editor.HasError);

        editor.Direction = editor.Directions.Single(choice => choice.Id == HabitRules.AtMost);
        editor.TargetText = "5";
        editor.SaveCommand.Execute(null);

        Assert.False(editor.IsOpen);
        var habit = planner.Habits.All().Single();
        Assert.Equal((HabitRules.PerMonth, 1, 5.0, HabitRules.AtMost), (habit.Cadence, habit.Times, habit.Target, habit.Direction));
    }

    [Fact]
    public void AWeeklyLimitCountsWhatTheWeekHadAndGoesOverMidWeek()
    {
        // 2026-09-18 is a Friday, so its week runs from Monday 14 September.
        var takeaway = planner.Habits.Add(new HabitDraft("Takeaway", Today.AddDays(-4))
        {
            Cadence = HabitRules.PerWeek,
            Times = 2,
            Direction = HabitRules.AtMost,
        })!;
        planner.Habits.CheckIn(takeaway.Id, Today.AddDays(-3));
        var row = Page().Rows.Single();
        Assert.Equal(("Habits.TimesWeek(2)", "Habits.LimitWeek(1,2)"), (row.CadenceText, row.StatusText));
        Assert.Equal(0.5, row.Fraction);
        Assert.False(row.IsOver);
        Assert.Equal([true, false], row.Pips.Select(pip => pip.IsOn));

        planner.Habits.CheckIn(takeaway.Id, Today.AddDays(-2));
        planner.Habits.CheckIn(takeaway.Id, Today);
        row = Page().Rows.Single();
        Assert.Equal("Habits.LimitWeek(3,2)", row.StatusText);
        Assert.True(row.IsOver);
        Assert.Equal([false, false, true], row.Pips.Select(pip => pip.IsOver));
        Assert.True(row.Dots.Last().IsOver);
    }

    [Fact]
    public void AMonthlyAmountLimitSaysWhatTheMonthHadInItsUnit()
    {
        var drinks = planner.Habits.Add(new HabitDraft("Drinks", new DateOnly(2026, 9, 1))
        {
            Cadence = HabitRules.PerMonth,
            Times = 1,
            Measure = HabitRules.Amount,
            Target = 5,
            Unit = "drinks",
            Direction = HabitRules.AtMost,
        })!;
        planner.Habits.CheckIn(drinks.Id, Today.AddDays(-10), 2);
        planner.Habits.CheckIn(drinks.Id, Today, 1);

        var row = Page().Rows.Single();
        Assert.Equal("Habits.LimitUnitMonth(3,5,drinks)", row.StatusText);
        Assert.False(row.IsOver);
    }

    [Fact]
    public void ADailyLimitOfZeroIsOverOnceAnythingIsHad()
    {
        var sweets = planner.Habits.Add(new HabitDraft("Sweets", Today)
        {
            Measure = HabitRules.Count,
            Target = 0,
            Direction = HabitRules.AtMost,
        })!;
        var row = Page().Rows.Single();
        Assert.Equal(("Habits.Limit(0,0)", 0.0), (row.StatusText, row.Fraction));
        Assert.False(row.IsOver);

        planner.Habits.CheckIn(sweets.Id, Today, 1);
        row = Page().Rows.Single();
        Assert.Equal(1.0, row.Fraction);
        Assert.True(row.IsOver);
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

    [Fact]
    public void HabitsSitInEveryDayWeeklyAndLimitsSoALimitNeverReadsAsNotDone()
    {
        planner.Habits.Add(new HabitDraft("Read", Today));
        planner.Habits.Add(new HabitDraft("Gym", Today) { Cadence = HabitRules.OnWeekdays, Weekdays = 21 });
        planner.Habits.Add(new HabitDraft("Run", Today) { Cadence = HabitRules.PerWeek, Times = 3 });
        planner.Habits.Add(new HabitDraft("Snacks", Today) { Measure = HabitRules.Count, Target = 2, Direction = HabitRules.AtMost });

        var page = Page();

        Assert.Equal(
            [
                ("HABITS.GROUPHEADER(HABITS.GROUPDAYS,2)", "Read, Gym"),
                ("HABITS.GROUPHEADER(HABITS.GROUPWEEKLY,1)", "Run"),
                ("HABITS.GROUPHEADER(HABITS.GROUPLIMITS,1)", "Snacks"),
            ],
            page.Groups.Select(group => (group.Header, string.Join(", ", group.Rows.Select(row => row.Name)))));
        Assert.Equal(HabitStanding.Limit, page.Groups.Last().Rows.Single().Standing);
        Assert.True(page.Rows.All(row => row.IsFull));
    }

    [Fact]
    public void HideDoneTakesTheDoneHabitsOutOfTheirGroupsAndKeepsTheCounts()
    {
        var read = planner.Habits.Add(new HabitDraft("Read", Today))!;
        planner.Habits.Add(new HabitDraft("Stretch", Today));
        var run = planner.Habits.Add(new HabitDraft("Run", Today) { Cadence = HabitRules.PerWeek, Times = 3 })!;
        planner.Habits.CheckIn(read.Id, Today);
        // One run today is today's part of a weekly habit, though the week needs two more.
        planner.Habits.CheckIn(run.Id, Today);
        var page = Page();

        page.HideDone = true;

        Assert.Equal(["Stretch"], page.Groups[0].Rows.Select(row => row.Name));
        Assert.Equal(2, page.Groups[0].Total);
        Assert.True(page.Groups[1].IsAllDone);
        Assert.Equal("Habits.ShowDone", page.HideDoneText);

        page.HideDone = false;
        Assert.Equal(["Read", "Stretch"], page.Groups[0].Rows.Select(row => row.Name));
    }

    [Fact]
    public void TheSummaryCountsWhatTodayAsksForAndNamesTheLongestStreak()
    {
        var read = planner.Habits.Add(new HabitDraft("Read", Today.AddDays(-5)) { Emoji = "📖" })!;
        var water = planner.Habits.Add(new HabitDraft("Water", Today) { Measure = HabitRules.Count, Target = 8 })!;
        var stretch = planner.Habits.Add(new HabitDraft("Stretch", Today))!;
        planner.Habits.Add(new HabitDraft("Snacks", Today) { Measure = HabitRules.Count, Target = 2, Direction = HabitRules.AtMost });
        foreach (var back in Enumerable.Range(0, 5))
        {
            planner.Habits.CheckIn(read.Id, Today.AddDays(-back));
        }

        planner.Habits.CheckIn(water.Id, Today, 4);
        planner.Habits.Skip(stretch.Id, Today);

        var page = Page();

        // Read and Water ask something of today; the limit and the skipped one don't.
        Assert.Equal(("1", "Habits.SummaryOf(2)", 0.75), (page.SummaryDone, page.SummaryOf, page.SummaryShare));
        Assert.Equal("Habits.SummaryBest(📖 Read,Habits.StreakDays(5))", page.SummaryBest);
        Assert.StartsWith("Habits.SummaryName(1,2)", page.SummaryName, StringComparison.Ordinal);
    }

    [Fact]
    public void ACardShowsPipsForASmallCountABarForAnAmountAndTheWeeksDots()
    {
        var water = planner.Habits.Add(new HabitDraft("Water", Today) { Measure = HabitRules.Count, Target = 8 })!;
        var snacks = planner.Habits.Add(new HabitDraft("Snacks", Today) { Measure = HabitRules.Count, Target = 2, Direction = HabitRules.AtMost })!;
        planner.Habits.Add(new HabitDraft("Run", Today) { Measure = HabitRules.Amount, Target = 5, Unit = "km" });
        var read = planner.Habits.Add(new HabitDraft("Read", Today.AddDays(-10)))!;
        planner.Habits.CheckIn(water.Id, Today, 5);
        planner.Habits.CheckIn(snacks.Id, Today, 3);
        planner.Habits.CheckIn(read.Id, Today.AddDays(-1));

        var page = Page();
        HabitRowViewModel Row(string name) => page.Rows.Single(row => row.Name == name);

        Assert.Equal((8, 5), (Row("Water").Pips.Count, Row("Water").Pips.Count(pip => pip.IsOn)));
        Assert.Equal([false, false, true], Row("Snacks").Pips.Select(pip => pip.IsOver));
        Assert.True(Row("Run").ShowsBar && !Row("Run").HasPips);
        Assert.False(Row("Read").HasPips || Row("Read").HasBar);
        Assert.Equal(7, Row("Read").Dots.Count);
        Assert.Equal([HabitDot.Missed, HabitDot.Met, HabitDot.Open], Row("Read").Dots.TakeLast(3).Select(dot => dot.Kind));
        Assert.True(Row("Read").Dots[^1].IsToday);
        Assert.Equal("Habits.Dots(1,5,0)", Row("Read").DotsText);
        Assert.True(Row("Water").ShowsPlusOne);
        Assert.Equal("Habits.AddOne(Water)", Row("Water").ButtonText);
    }

    [Fact]
    public void AnArchivedHabitLeavesTheGroupsAndHasNoButton()
    {
        planner.Habits.Add(new HabitDraft("Read", Today));
        var old = planner.Habits.Add(new HabitDraft("Cold shower", Today.AddDays(-30)))!;
        planner.Habits.SetArchived(old.Id, true);

        var page = Page();

        Assert.Equal(["Read"], page.Groups.SelectMany(group => group.Rows).Select(row => row.Name));
        var archived = page.Archived.Single();
        Assert.False(archived.ShowsButton);
        Assert.Equal("Habits.Line(Habits.CadenceDaily,Habits.ArchivedLine)", archived.CardLine);
    }

    private HabitsViewModel Page() =>
        new(planner.Habits, planner.Goals, planner.Settings, planner.Strings, planner.Time, () => motionReduced, action => action());
}
