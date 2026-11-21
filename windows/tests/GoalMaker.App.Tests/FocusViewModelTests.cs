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
}
