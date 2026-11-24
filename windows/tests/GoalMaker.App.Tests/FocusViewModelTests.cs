using GoalMaker.App.ViewModels;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>
/// The Focus Board on Windows (docs/focus.md): the add panel's search and filter, making a task straight
/// onto the board from a typed line, and the board's own filter, which keeps hidden tasks in their places.
/// </summary>
public sealed class FocusViewModelTests : IDisposable
{
    private readonly TestPlanner planner = new();

    public void Dispose() => planner.Dispose();

    private FocusViewModel Page() => new(
        planner.Tasks, planner.Areas, planner.Tags, planner.Projects, planner.Settings, planner.Strings, _ => null, planner.Time, action => action());

    private static List<string> Board(FocusViewModel page) => [.. page.Items.Select(row => row.Title)];

    private static List<string> Offered(FocusViewModel page) => [.. page.Candidates.Select(row => row.Title)];

    [Fact]
    public void TheAddPanelFindsEveryWordIgnoringCaseAndAccents()
    {
        planner.Tasks.Add("Studijní: prodloužit ISIC");
        planner.Tasks.Add("Studijní: zrušit dipl. práci");
        planner.Tasks.Add("See internship hours");
        var page = Page();

        page.ShowAddDialogCommand.Execute(null);
        Assert.Equal(3, Offered(page).Count);
        Assert.True(page.IsNoMatch is false && page.IsAllOnBoard is false);

        page.Query = "studijni ZRUSIT";
        Assert.Equal(["Studijní: zrušit dipl. práci"], Offered(page));
        Assert.Equal("Focus.Create(studijni ZRUSIT)", page.CreateText);

        page.Query = "dentist";
        Assert.Empty(Offered(page));
        Assert.True(page.IsNoMatch);
    }

    [Fact]
    public void WhatIsTypedInTheAddPanelBecomesATaskAtTheEndOfTheBoard()
    {
        var first = planner.Tasks.Add("Extend the ISIC card")!;
        var page = Page();
        page.ShowAddDialogCommand.Execute(null);
        page.AddToBoardCommand.Execute(page.Candidates.Single());

        page.Query = "Submit the project @School";
        Assert.True(page.CreateFromQueryCommand.CanExecute(null));
        page.CreateFromQueryCommand.Execute(null);

        Assert.False(page.IsAddOpen);
        Assert.Equal(string.Empty, page.Query);
        Assert.Equal(["Extend the ISIC card", "Submit the project"], Board(page));
        var made = planner.Tasks.All().Single(task => task.Title == "Submit the project");
        Assert.Equal("School", planner.Areas.All().Single(area => area.Id == made.AreaId).Name);
        Assert.True(made.FocusPosition > planner.Tasks.All().Single(task => task.Id == first.Id).FocusPosition);
    }

    [Fact]
    public void TheQuickAddFieldKeepsItsTextWhenThereIsNothingToSave()
    {
        var page = Page();
        page.QuickAddTitle = "   ";
        page.QuickAddCommand.Execute(null);
        Assert.Equal("   ", page.QuickAddTitle);
        Assert.True(page.IsEmpty);

        page.QuickAddTitle = "See internship hours";
        page.QuickAddCommand.Execute(null);
        Assert.Equal(string.Empty, page.QuickAddTitle);
        Assert.Equal(["See internship hours"], Board(page));
    }

    [Fact]
    public void TheFilterNarrowsTheBoardAndAMoveKeepsHiddenTasksInPlace()
    {
        var page = Page();
        foreach (var line in new[] { "One @School", "Two", "Three @School", "Four" })
        {
            page.QuickAddTitle = line;
            page.QuickAddCommand.Execute(null);
        }

        page.Filters.SelectedArea = page.Filters.AreaChoices.Single(choice => choice.Id is not null);
        Assert.Equal(["One", "Three"], Board(page));

        page.MoveUpCommand.Execute(page.Items[1]);
        Assert.Equal(["Three", "One"], Board(page));

        page.Filters.ClearCommand.Execute(null);
        Assert.Equal(["Three", "One", "Two", "Four"], Board(page));
    }

    [Fact]
    public void AFilterThatHidesTheWholeBoardSaysSo()
    {
        planner.Areas.Create("Home");
        var page = Page();
        page.QuickAddTitle = "Hand in the form";
        page.QuickAddCommand.Execute(null);

        page.Filters.SelectedArea = page.Filters.AreaChoices.Single(choice => choice.Id is not null);
        Assert.Empty(page.Items);
        Assert.False(page.IsEmpty);
        Assert.True(page.IsFilteredEmpty);
    }

    [Fact]
    public void RowsKnowTheirPlaceAndWhichWayTheyCanMove()
    {
        var page = Page();
        foreach (var line in new[] { "One", "Two", "Three" })
        {
            page.QuickAddTitle = line;
            page.QuickAddCommand.Execute(null);
        }

        Assert.Equal([1, 2, 3], page.Items.Select(row => row.Place));
        Assert.Equal([false, true, true], page.Items.Select(row => row.CanMoveUp));
        Assert.Equal([true, true, false], page.Items.Select(row => row.CanMoveDown));
        Assert.Equal("Focus.Count(3)", page.CountText);

        page.MoveDownCommand.Execute(page.Items[0]);
        Assert.Equal(["Two", "One", "Three"], Board(page));
        Assert.Equal(2, page.Items.Single(row => row.Title == "One").Place);
    }

    [Fact]
    public void TheCountSaysHowManyTheFilterShows()
    {
        var page = Page();
        Assert.Equal(string.Empty, page.CountText);
        foreach (var line in new[] { "One @School", "Two", "Three" })
        {
            page.QuickAddTitle = line;
            page.QuickAddCommand.Execute(null);
        }

        page.Filters.SelectedArea = page.Filters.AreaChoices.Single(choice => choice.Id is not null);
        Assert.Equal("Focus.CountFiltered(1,3)", page.CountText);
    }

    [Fact]
    public void ALateDateIsLateByTheAppsClockAndTheDeadlineComesFirst()
    {
        // The planner's clock says 18 September 2026, whatever day the tests run on.
        var late = planner.Tasks.Add("Hand in the form")!;
        planner.Tasks.SetDeadline(late.Id, new DateOnly(2026, 9, 16));
        var soon = planner.Tasks.Add("Renew the card")!;
        planner.Tasks.SetDeadline(soon.Id, new DateOnly(2026, 9, 21));
        var page = Page();
        page.AddToBoardCommand.Execute(page.Candidates.Single(row => row.Id == late.Id));

        var row = page.Items.Single();
        Assert.True(row.IsLate);
        Assert.True(row.HasDate);
        var offered = page.Candidates.Single();
        Assert.Equal(soon.Id, offered.Id);
        Assert.False(offered.IsLate);
        Assert.True(offered.HasDate);
    }

    [Fact]
    public void TheAddPanelStaysCurrentWhileClosedForTheWideWindow()
    {
        planner.Tasks.Add("Fix the bike light");
        var page = Page();
        Assert.False(page.IsAddOpen);
        Assert.Equal(["Fix the bike light"], Offered(page));

        planner.Tasks.Add("Book the dentist");
        Assert.Equal(2, Offered(page).Count);

        page.Query = "dentist";
        Assert.Equal(["Book the dentist"], Offered(page));

        page.AddToBoardCommand.Execute(page.Candidates.Single());
        Assert.Equal(["Book the dentist"], Board(page));
        Assert.True(page.IsNoMatch);
    }

    [Fact]
    public void EscInTheSearchBoxClearsItFirstThenClosesThePanel()
    {
        var page = Page();
        page.ShowAddDialogCommand.Execute(null);
        page.Query = "bike";

        page.EscapeSearchCommand.Execute(null);
        Assert.Equal(string.Empty, page.Query);
        Assert.True(page.IsAddOpen);

        page.EscapeSearchCommand.Execute(null);
        Assert.False(page.IsAddOpen);
    }

    [Fact]
    public void WithNoOpenTasksTheAddPanelSaysSoInsteadOfAllOnTheBoard()
    {
        var page = Page();
        Assert.True(page.HasNoOpenTasks);

        page.QuickAddTitle = "Fix the bike light";
        page.QuickAddCommand.Execute(null);
        Assert.False(page.HasNoOpenTasks);
        Assert.True(page.IsAllOnBoard);
    }

    [Fact]
    public void ALateRowSaysLateToAScreenReaderNotOnlyInColor()
    {
        var late = planner.Tasks.Add("Hand in the form @School")!;
        planner.Tasks.SetDeadline(late.Id, new DateOnly(2026, 9, 16));
        var other = planner.Tasks.Add("Renew the card")!;
        planner.Tasks.SetDeadline(other.Id, new DateOnly(2026, 9, 10));
        var page = Page();
        page.AddToBoardCommand.Execute(page.Candidates.Single(row => row.Id == late.Id));

        var row = page.Items.Single();
        Assert.StartsWith("Focus.Late · ", row.HelpText);
        Assert.EndsWith(row.Subtitle, row.HelpText);
        Assert.StartsWith("Focus.Late · ", page.Candidates.Single().HelpText);
    }

    [Fact]
    public void ARowThatIsNotLateReadsItsDateAndArea()
    {
        var page = Page();
        page.QuickAddTitle = "Hand in the form @School";
        page.QuickAddCommand.Execute(null);
        Assert.Equal("School", page.Items.Single().HelpText);
    }

    [Fact]
    public void TheDoneBoxUnticksWhenTheTaskStaysOpen()
    {
        var page = Page();
        page.QuickAddTitle = "Hand in the form";
        page.QuickAddCommand.Execute(null);
        // A row for a task the store does not know: completing it changes nothing, so the box must not stay ticked.
        var row = new FocusRowViewModel(planner.Task("Hand in the form") with { Id = "not-in-the-store" }, null, new DateOnly(2026, 9, 18), 1, 1, "Late");
        row.IsDone = true;
        page.CompleteCommand.Execute(row);
        Assert.False(row.IsDone);
    }

    [Fact]
    public void TickingTheDoneBoxCompletesTheTaskAndTakesItOffTheBoard()
    {
        var page = Page();
        page.QuickAddTitle = "Hand in the form";
        page.QuickAddCommand.Execute(null);
        var row = page.Items.Single();

        row.IsDone = true;
        page.CompleteCommand.Execute(row);
        Assert.Empty(page.Items);
        Assert.Equal(TaskState.Done, planner.Tasks.All().Single().State);
    }
}
