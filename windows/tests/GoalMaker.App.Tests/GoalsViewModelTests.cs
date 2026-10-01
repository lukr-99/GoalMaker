using System.Globalization;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.Tests;

/// <summary>The Windows Goals page over a real replica: periods, progress, the rings, filtering, the lit chain, the quick log, copying, the editor, logging and the list view.</summary>
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
        Assert.Equal("Goals.Feeds(Half marathon)", page.Lanes[2].Rows.Single(row => row.Title == "3 runs").Feeds);
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

    [Fact]
    public void TheRingsCountEachHorizonsGoalsHitsAndHowFarAlongTheyAre()
    {
        var week = new DateOnly(2026, 9, 14);
        var km = planner.Goals.Add(new GoalDraft("Run 20 km", GoalHorizon.Week, week, GoalRules.ModeNumber, Target: 20, Unit: "km"))!;
        planner.Goals.LogAmount(km.Id, new DateOnly(2026, 9, 15), 5);
        var book = planner.Goals.Add(new GoalDraft("Book the race", GoalHorizon.Week, week))!;
        planner.Goals.SetStatus(book.Id, GoalRules.Done);
        planner.Goals.Add(new GoalDraft("Call grandma", GoalHorizon.Week, week));
        planner.Goals.Add(new GoalDraft("Read a book", GoalHorizon.Month, new DateOnly(2026, 9, 1)));
        var page = Page();

        Assert.Equal([GoalHorizon.Year, GoalHorizon.Month, GoalHorizon.Week, GoalHorizon.Day], page.Rings.Select(ring => ring.Horizon));
        var ring = page.Rings[2];
        Assert.Equal((1, 3, "Goals.RingHit(1,3)"), (ring.Hits, ring.Count, ring.HitText));
        Assert.Equal((0.25 + 1 + 0) / 3, ring.Fraction, 9);
        Assert.Equal((0, 1), (page.Rings[1].Hits, page.Rings[1].Count));

        // Friday: 5 of 20 km is behind with 4 of 7 days gone; Call grandma is still on track.
        Assert.Equal(1, page.Behind);
        Assert.Equal("Goals.Hint Goals.NeedYouOne(1)", page.HintText);
        var rows = page.Lanes[2].Rows;
        Assert.Equal(["Run 20 km", "Call grandma", "Book the race"], rows.Select(row => row.Title));
        Assert.Equal(
            ("Goals.PaceBehindUnit(7,km)", "Goals.PaceOnTrack", "Goals.PaceHit"),
            (rows[0].PaceText, rows[1].PaceText, rows[2].PaceText));
        Assert.Equal(("5", "Goals.OfUnit(20,km)", "Goals.QuickUnit(5,km)"), (rows[0].ValueText, rows[0].OfText, rows[0].QuickText));
        Assert.Equal("Goals.LaneLine(Goals.RingHit(1,3),Goals.DayOf(5,7))", page.Lanes[2].Line);
    }

    [Fact]
    public void ARingShowsOnlyItsColumnAndAClickAgainShowsAll()
    {
        var page = Page();

        page.Rings[1].FilterCommand.Execute(null);

        Assert.Equal(GoalHorizon.Month, page.Filter);
        Assert.Equal([false, true, false, false], page.Rings.Select(ring => ring.IsShown));
        Assert.Equal("Goals.RingShown", page.Rings[1].Status);
        Assert.Equal([true, false, true, true], page.Lanes.Select(lane => lane.IsDimmed));
        Assert.True(page.NextWeek!.IsDimmed);

        page.Rings[2].FilterCommand.Execute(null);
        Assert.Equal([true, true, false, true], page.Lanes.Select(lane => lane.IsDimmed));
        Assert.False(page.NextWeek!.IsDimmed);

        page.Rings[2].FilterCommand.Execute(null);
        Assert.Null(page.Filter);
        Assert.All(page.Sections, section => Assert.False(section.IsDimmed));
    }

    [Fact]
    public void ClickingACardLightsWhatItFeedsAndWhatFeedsIt()
    {
        var year = planner.Goals.Add(new GoalDraft("Half marathon", GoalHorizon.Year, new DateOnly(2026, 1, 1)))!;
        var month = planner.Goals.Add(new GoalDraft("Run 80 km", GoalHorizon.Month, new DateOnly(2026, 9, 1), ParentId: year.Id))!;
        var week = planner.Goals.Add(new GoalDraft("3 runs", GoalHorizon.Week, new DateOnly(2026, 9, 14), ParentId: month.Id))!;
        planner.Goals.Add(new GoalDraft("Read a book", GoalHorizon.Month, new DateOnly(2026, 9, 1)));
        planner.Goals.Add(new GoalDraft("Run 6 km", GoalHorizon.Day, new DateOnly(2026, 9, 18), ParentId: week.Id));
        var page = Page();

        page.Lanes[1].Rows.Single(row => row.Title == "Run 80 km").PickCommand.Execute(null);

        Assert.True(page.HasPick);
        Assert.Equal("Goals.ChainOf(Run 80 km)", page.HintText);
        var cards = page.Lanes.SelectMany(lane => lane.Rows).ToDictionary(row => row.Title);
        Assert.Equal(["Half marathon", "Run 80 km", "3 runs", "Run 6 km"], cards.Values.Where(card => card.IsLit).Select(card => card.Title));
        Assert.True(cards["Read a book"].IsDimmed);
        Assert.Equal(0.32, cards["Read a book"].CardOpacity);
        Assert.Equal((3.0, "Goals.Picked"), (cards["Run 80 km"].ChainBorder, cards["Run 80 km"].Status));
        Assert.Equal((2.0, "Goals.InChain"), (cards["3 runs"].ChainBorder, cards["3 runs"].Status));
        Assert.Equal("Goals.Feeds(Run 80 km)", cards["3 runs"].Feeds);
    }

    [Fact]
    public void ClearOrASecondClickPutsTheChainOut()
    {
        var goal = planner.Goals.Add(new GoalDraft("3 runs", GoalHorizon.Week, new DateOnly(2026, 9, 14)))!;
        var page = Page();
        var card = page.Lanes[2].Rows.Single();

        card.PickCommand.Execute(null);
        Assert.True(card.IsPicked);
        page.ClearPickCommand.Execute(null);
        Assert.False(page.HasPick);
        Assert.False(card.IsPicked || card.IsDimmed);

        card.PickCommand.Execute(null);
        planner.Goals.Add(new GoalDraft("Read", GoalHorizon.Week, new DateOnly(2026, 9, 14)));
        page.Refresh();
        Assert.Equal(goal.Id, page.Picked?.Id);
        Assert.True(page.Lanes[2].Rows.Single(row => row.Title == "Read").IsDimmed);
        page.Picked!.PickCommand.Execute(null);
        Assert.False(page.HasPick);
    }

    [Fact]
    public void TheQuickLogRepeatsTheLatestAmountOrAsksForOne()
    {
        var week = new DateOnly(2026, 9, 14);
        var km = planner.Goals.Add(new GoalDraft("Run 20 km", GoalHorizon.Week, week, GoalRules.ModeNumber, Target: 20, Unit: "km"))!;
        planner.Goals.Add(new GoalDraft("Swim 2 km", GoalHorizon.Week, week, GoalRules.ModeNumber, Target: 2, Unit: "km"));
        planner.Goals.LogAmount(km.Id, week, 7.5);
        var page = Page();
        var run = page.Lanes[2].Rows.Single(row => row.Title == "Run 20 km");
        var seven = GoalRowViewModel.Amount(7.5);
        Assert.Equal(($"Goals.QuickUnit({seven},km)", $"Goals.QuickName(Goals.QuickUnit({seven},km),Run 20 km)"), (run.QuickText, run.QuickName));

        run.QuickLogCommand.Execute(null);

        Assert.Equal("Goals.AmountUnit(15,20,km)", page.Lanes[2].Rows.Single(row => row.Title == "Run 20 km").ProgressText);
        Assert.Equal(new DateOnly(2026, 9, 18), planner.Goals.Entries().Single(entry => entry.Day != week).Day);
        var swim = page.Lanes[2].Rows.Single(row => row.Title == "Swim 2 km");
        Assert.Equal("Goals.LogShort", swim.QuickText);
        swim.QuickLogCommand.Execute(null);
        Assert.True(page.IsLogging);
        Assert.Equal("Goals.LogTitle(Swim 2 km)", page.LogTitle);
    }

    [Fact]
    public void NextWeekOffersThisWeeksGoalsAndCopyingBringsThemOver()
    {
        var month = planner.Goals.Add(new GoalDraft("Run 80 km", GoalHorizon.Month, new DateOnly(2026, 9, 1)))!;
        planner.Goals.Add(new GoalDraft("3 runs", GoalHorizon.Week, new DateOnly(2026, 9, 14), ParentId: month.Id));
        var page = Page();
        var next = page.NextWeek!;
        Assert.True(next.IsEmpty && next.CanCopy);
        Assert.Equal("Goals.CopyThisWeek", next.CopyLabel);

        next.CopyCommand.Execute(null);

        var copied = page.NextWeek!.Rows.Single();
        Assert.Equal(("3 runs", new DateOnly(2026, 9, 21)), (copied.Title, copied.Goal.PeriodStart));
        // Next week still overlaps September, so it keeps feeding the month.
        Assert.Equal(month.Id, copied.Goal.ParentId);
        Assert.False(page.NextWeek!.CanCopy);
    }

    [Fact]
    public void TheGoalsShowAsTheLadderAtFirst()
    {
        var page = Page();

        Assert.Equal(GoalsView.Ladder, page.View);
        Assert.True(page.IsLadderView);
        Assert.False(page.IsListView);
    }

    [Fact]
    public void TheListViewIsRememberedOnThisPcAndPutsALitChainOut()
    {
        var goal = planner.Goals.Add(new GoalDraft("3 runs", GoalHorizon.Week, new DateOnly(2026, 9, 14)))!;
        var page = Page();
        page.Pick(goal.Id);

        page.IsListView = true;

        Assert.Equal((GoalsView.List, false, true), (page.View, page.IsLadderView, page.IsListView));
        Assert.Null(page.Picked);
        Assert.Equal(GoalsView.List, planner.Settings.GoalsView);
        Assert.Equal(GoalsView.List, Page().View);

        page.IsLadderView = true;

        Assert.Equal(GoalsView.Ladder, planner.Settings.GoalsView);
    }

    [Fact]
    public void TheListGroupsEveryPeriodInOrderWithTheGoalsThatNeedYouFirstWhateverTheRingsShow()
    {
        var week = new DateOnly(2026, 9, 14);
        planner.Goals.Add(new GoalDraft("Half marathon", GoalHorizon.Year, new DateOnly(2026, 1, 1)));
        planner.Goals.Add(new GoalDraft("Read a book", GoalHorizon.Month, new DateOnly(2026, 9, 1)));
        var book = planner.Goals.Add(new GoalDraft("Book the race", GoalHorizon.Week, week))!;
        planner.Goals.SetStatus(book.Id, GoalRules.Done);
        planner.Goals.Add(new GoalDraft("Call grandma", GoalHorizon.Week, week));
        var km = planner.Goals.Add(new GoalDraft("Run 20 km", GoalHorizon.Week, week, GoalRules.ModeNumber, Target: 20, Unit: "km"))!;
        planner.Goals.LogAmount(km.Id, new DateOnly(2026, 9, 15), 5);
        planner.Goals.Add(new GoalDraft("Inbox zero", GoalHorizon.Day, new DateOnly(2026, 9, 18)));
        planner.Goals.Add(new GoalDraft("Plan the trip", GoalHorizon.Week, new DateOnly(2026, 9, 21)));
        var page = Page();
        page.ToggleFilter(GoalHorizon.Day);

        page.IsListView = true;

        Assert.Equal(
            [GoalHorizon.Year, GoalHorizon.Month, GoalHorizon.Week, GoalHorizon.Day, GoalHorizon.Week],
            page.Sections.Select(section => section.Horizon));
        Assert.Equal(
            [["Half marathon"], ["Read a book"], ["Run 20 km", "Call grandma", "Book the race"], ["Inbox zero"], ["Plan the trip"]],
            page.Sections.Select(section => section.Rows.Select(row => row.Title).ToArray()).ToArray());
        Assert.Equal(GoalPace.Behind, page.Sections[2].Rows[0].Pace);
        Assert.Equal("Goals.RingHit(1,3)", page.Sections[2].HitText);
        Assert.StartsWith("Goals.AddTo(Goals.Section(Goals.NextWeek,", page.Sections[4].AddName, StringComparison.Ordinal);
    }

    [Fact]
    public void ARowInTheListOpensTheGoalsEditor()
    {
        planner.Goals.Add(new GoalDraft("Run 20 km", GoalHorizon.Week, new DateOnly(2026, 9, 14), GoalRules.ModeNumber, Target: 20, Unit: "km"));
        var page = Page();
        page.IsListView = true;

        page.Sections[2].Rows.Single().EditCommand.Execute(null);

        Assert.True(page.Editor.IsOpen);
        Assert.False(page.ShowList);
        Assert.Equal("Run 20 km", page.Editor.Title);
    }

    private GoalsViewModel Page() => new(planner.Goals, planner.Tasks, planner.Settings, planner.Strings, planner.Time, () => motionReduced, action => action());

    private TaskItem Add(string line)
    {
        var task = planner.Tasks.Add(ComposerParser.Parse(line, planner.Time.GetLocalNow().DateTime))!;
        planner.Time.Advance(TimeSpan.FromSeconds(1));
        return task;
    }
}
