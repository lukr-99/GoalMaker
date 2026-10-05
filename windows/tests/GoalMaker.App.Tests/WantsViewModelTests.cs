using GoalMaker.App.ViewModels;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.Tests;

/// <summary>The Wants page over a real replica: filters, rings, the add panel, deciding with undo, thresholds (M8-04).</summary>
public sealed class WantsViewModelTests : IDisposable
{
    private readonly TestPlanner planner = new();

    public WantsViewModelTests() =>
        planner.Time.SetUtcNow(new DateTimeOffset(2026, 9, 28, 12, 0, 0, TimeSpan.Zero));

    public void Dispose() => planner.Dispose();

    private WantsViewModel Page() => new(planner.Wants, planner.Settings, new TestPlanner.FormatStrings(), planner.Time, action => action());

    [Fact]
    public void CoolingLeadsWhileNothingIsReadyAndTheRingCountsDown()
    {
        planner.Wants.Add(new WantDraft("Lamp", "Dark desk", Price: 450));
        planner.Time.Advance(TimeSpan.FromDays(3));

        var page = Page();

        Assert.Equal(WantState.Cooling, page.Filter);
        var row = Assert.Single(page.Rows);
        Assert.Equal("4", row.RingText);
        Assert.Equal(3.0 / 7.0, row.Fraction, 9);
        Assert.Equal("Wants.Cooling 1", page.CoolingLabel);
        Assert.Equal("Wants.Ready", page.ReadyLabel);
    }

    [Fact]
    public void ReadyLeadsOnceAnythingIsReadyAndTheChosenFilterSticks()
    {
        planner.Wants.Add(new WantDraft("Kindle", "Reading at night", PickedDays: 0));
        planner.Wants.Add(new WantDraft("Desk", "Back pain", Price: 12900));
        var page = Page();

        Assert.Equal(WantState.Ready, page.Filter);
        Assert.Equal(["Kindle"], page.Rows.Select(row => row.Title));
        Assert.True(page.Rows[0].IsReady);

        page.ShowCommand.Execute(WantState.Cooling);
        page.Refresh();

        Assert.Equal(WantState.Cooling, page.Filter);
        Assert.Equal(["Desk"], page.Rows.Select(row => row.Title));
    }

    [Fact]
    public void AWantClaudeAddedSaysSoAndTheOwnersDoNot()
    {
        planner.Wants.Add(new WantDraft("Kindle", "Reading at night", PickedDays: 0));
        planner.Wants.Add(new WantDraft("Lamp", "Dark desk", PickedDays: 0));
        // Claude added this one through the connector, and that is how it arrives from the server.
        var row = planner.Replica.Get("wants", planner.Wants.All().Single(want => want.Title == "Kindle").Id)!;
        row["made_by"] = ProjectRules.Claude;
        planner.Replica.Put("wants", row);

        var page = Page();

        Assert.EndsWith("Wants.ByClaude", page.Rows.Single(one => one.Title == "Kindle").Subtitle);
        Assert.DoesNotContain("Wants.ByClaude", page.Rows.Single(one => one.Title == "Lamp").Subtitle);
    }

    [Fact]
    public void TheAddPanelShowsTheCooldownAPriceGivesAndCanPickAnother()
    {
        var page = Page();
        page.StartAdding("Trail shoes");

        Assert.True(page.IsEditing);
        Assert.Equal("Trail shoes", page.DraftTitle);
        Assert.False(page.SaveDraftCommand.CanExecute(null));
        page.DraftReason = "The old ones have holes";
        page.DraftPrice = "3 400";
        Assert.Equal("Wants.Days(30)", page.DraftDays);
        page.FewerDaysCommand.Execute(null);
        Assert.Equal("Wants.Days(29)", page.DraftDays);

        page.SaveDraftCommand.Execute(null);

        Assert.False(page.IsEditing);
        var want = Assert.Single(planner.Wants.All());
        Assert.Equal(3400, want.Price);
        Assert.Equal(29, want.CooldownDays);
    }

    [Fact]
    public void DecidingMovesAWantToDecidedAndUndoTakesItBack()
    {
        planner.Wants.Add(new WantDraft("Kindle", "Reading at night", PickedDays: 0));
        var page = Page();
        var row = page.Rows[0];
        row.Note = "Library card works";

        row.DropCommand.Execute(null);

        Assert.True(page.HasUndo);
        Assert.Equal("Wants.DroppedMessage(Kindle)", page.UndoText);
        Assert.Equal("Wants.Decided 1", page.DecidedLabel);
        Assert.Equal("Library card works", planner.Wants.All()[0].DecisionNote);

        page.UndoCommand.Execute(null);

        Assert.Null(planner.Wants.All()[0].Decision);
        Assert.False(page.HasUndo);
    }

    [Fact]
    public void TheNeedsTabListsOpenNeedsByTheirDayAndALateOneSaysSo()
    {
        planner.Wants.Add(new WantDraft("Winter tyres", string.Empty, Price: 12900, Kind: WantRules.Need, NeedBy: new DateOnly(2026, 11, 1)));
        planner.Wants.Add(new WantDraft("printer ink", string.Empty, Kind: WantRules.Need));
        planner.Wants.Add(new WantDraft("Coat", "The zip broke", Kind: WantRules.Need, NeedBy: new DateOnly(2026, 9, 30)));
        planner.Wants.Add(new WantDraft("Kindle", "Reading at night", PickedDays: 10));
        planner.Time.Advance(TimeSpan.FromDays(3));

        var page = Page();
        page.IsNeedsTab = true;

        Assert.Equal(["Coat", "Winter tyres", "printer ink"], page.NeedRows.Select(row => row.Title));
        Assert.True(page.NeedRows[0].IsLate);
        Assert.StartsWith("Wants.NeedBy(", page.NeedRows[0].Need.Due, StringComparison.Ordinal);
        Assert.False(page.NeedRows[1].IsLate);
        Assert.StartsWith(WantsViewModel.Money(12900, "CZK") + " · ", page.NeedRows[1].Need.Before, StringComparison.Ordinal);
        Assert.Equal((string.Empty, string.Empty, string.Empty), (page.NeedRows[2].Need.Before, page.NeedRows[2].Need.Due, page.NeedRows[2].Need.After));
        Assert.Equal("Wants.Needs 3", page.NeedsLabel);

        // The wants leave the needs out, though a need is ready the day it is added.
        Assert.Equal(["Kindle"], page.Rows.Select(row => row.Title));
        Assert.Equal("Wants.Ready", page.ReadyLabel);
        Assert.Equal(WantState.Cooling, page.Filter);
    }

    [Fact]
    public void ABoughtNeedFoldsBelowAndReopenBringsItBack()
    {
        planner.Wants.Add(new WantDraft("Batteries", string.Empty, Kind: WantRules.Need));
        var page = Page();
        page.IsNeedsTab = true;
        Assert.False(page.HasNeedsDone);

        page.NeedRows[0].BuyCommand.Execute(null);

        Assert.Empty(page.NeedRows);
        Assert.True(page.HasNoOpenNeeds);
        Assert.Equal("Wants.NoNeedsOpen", page.NeedsEmptyText);
        Assert.True(page.HasNeedsDone);
        Assert.Equal("Wants.NeedsDone(1)", page.NeedsDoneLabel);
        var done = Assert.Single(page.NeedsDoneRows);
        Assert.Equal("Wants.StatusBought", done.Need.After);
        Assert.Equal("Wants.BoughtMessage(Batteries)", page.UndoText);
        Assert.Equal("Wants.Decided", page.DecidedLabel);

        done.ReopenCommand.Execute(null);

        Assert.Equal(["Batteries"], page.NeedRows.Select(row => row.Title));
        Assert.False(page.HasNeedsDone);
    }

    [Fact]
    public void TheAddPanelOnNeedsAddsANeedWithNoCooldownAndNoReason()
    {
        var page = Page();
        page.IsNeedsTab = true;
        page.AddCommand.Execute(null);

        Assert.True(page.IsDraftNeed);
        Assert.False(page.ShowsDraftDays);
        Assert.Equal("Wants.AddNeed", page.PanelTitle);
        Assert.Equal("Wants.NeedNoteField", page.ReasonLabel);
        page.DraftTitle = "Winter tyres";
        page.DraftPrice = "12 900";
        page.DraftNeedBy = new DateTime(2026, 11, 1);
        Assert.True(page.SaveDraftCommand.CanExecute(null));

        page.SaveDraftCommand.Execute(null);

        var need = Assert.Single(planner.Wants.All());
        Assert.Equal((WantRules.Need, 0, new DateOnly(2026, 11, 1), 12900.0), (need.Kind, need.CooldownDays, need.NeedBy, need.Price));
        Assert.Equal(["Winter tyres"], page.NeedRows.Select(row => row.Title));
    }

    [Fact]
    public void ThePageRemembersTheTabAndWantFromAComposerOpensTheWants()
    {
        var page = Page();
        Assert.True(page.IsWantsTab);
        Assert.False(page.Bar.ForNeeds);

        page.IsNeedsTab = true;

        Assert.Equal(WantsTab.Needs, planner.Settings.WantsTab);
        var again = Page();
        Assert.True(again.IsNeedsTab);
        Assert.True(again.Bar.ForNeeds);

        again.StartAdding("Kindle");

        Assert.True(again.IsWantsTab);
        Assert.False(again.IsDraftNeed);
        Assert.True(again.ShowsDraftDays);
        Assert.False(again.SaveDraftCommand.CanExecute(null));
        Assert.Equal(WantsTab.Wants, planner.Settings.WantsTab);
    }

    [Fact]
    public void ALineTypedOnTheNeedsTabAddsANeed()
    {
        var page = Page();
        page.IsNeedsTab = true;

        page.Bar.Line = "Batteries 120 Kč";
        page.Bar.SendCommand.Execute(null);

        var need = Assert.Single(planner.Wants.All());
        Assert.Equal((WantRules.Need, "Batteries", 120.0, 0), (need.Kind, need.Title, need.Price, need.CooldownDays));
        Assert.False(page.IsEditing);
    }

    [Fact]
    public void ThresholdsThatDontFitAreRefusedAndGoodOnesAreKept()
    {
        var page = Page();
        page.EditCooldownsCommand.Execute(null);
        Assert.Equal("1000", page.SmallUnder);

        page.MediumUnder = "500";
        page.SaveCooldownsCommand.Execute(null);
        Assert.True(page.CooldownsRefused);
        Assert.True(page.IsEditingCooldowns);

        page.MediumUnder = "20000";
        page.LargeDays = "60";
        page.SaveCooldownsCommand.Execute(null);

        Assert.False(page.IsEditingCooldowns);
        Assert.Equal(60, planner.Wants.Cooldowns().LargeDays);
        Assert.Contains("60", page.CooldownsLine, StringComparison.Ordinal);
    }
}
