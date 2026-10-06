using GoalMaker.App.Startup;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>
/// Project item ids on Windows (docs/projects.md, "Item ids"): the key in the project form, the id on a
/// card, the item's page and a list chip, Copy id, and finding an item by its id on the board, in the
/// archive and in Go to. The server gives the numbers, so these tests put them in as a sync would.
/// </summary>
public sealed class ItemIdTests : IDisposable
{
    private readonly TestPlanner planner = new();
    private readonly List<string> copied = [];

    public void Dispose() => planner.Dispose();

    [Fact]
    public void ANewProjectSuggestsAKeyFromItsName()
    {
        var page = Page();
        page.NewCommand.Execute(null);

        page.ProjectName = "GoalMaker";
        Assert.Equal("GM", page.ProjectKey);
        Assert.Equal("Projects.KeyHint(GM-12)", page.KeyHint);
        page.ProjectName = "Jsi na tahu";
        Assert.Equal("JNT", page.ProjectKey);

        page.SaveCommand.Execute(null);
        Assert.Equal("JNT", planner.Projects.All().Single().ItemKey);
    }

    [Fact]
    public void TheSuggestionSteersClearOfTheOtherProjectsKeys()
    {
        var other = planner.Projects.Add(new ProjectDraft("Game master"))!;
        planner.Projects.SetItemKey(other.Id, "GM");
        var page = Page();

        page.NewCommand.Execute(null);
        page.ProjectName = "GoalMaker";

        Assert.Equal("GM2", page.ProjectKey);
    }

    [Fact]
    public void AKeyTheOwnerTypedStaysWhenTheNameChanges()
    {
        var page = Page();
        page.NewCommand.Execute(null);
        page.ProjectName = "GoalMaker";

        page.ProjectKey = "app";
        page.ProjectName = "GoalMaker app";
        page.SaveCommand.Execute(null);

        Assert.Equal("APP", planner.Projects.All().Single().ItemKey);
    }

    [Fact]
    public void AKeyThatCantBeSavedKeepsTheFormOpenWithTheReason()
    {
        var other = planner.Projects.Add(new ProjectDraft("Game master"))!;
        planner.Projects.SetItemKey(other.Id, "GM");
        var page = Page();
        page.NewCommand.Execute(null);
        page.ProjectName = "GoalMaker";

        page.ProjectKey = "1GM";
        page.SaveCommand.Execute(null);
        Assert.True(page.IsEditing);
        Assert.Equal("Projects.KeyNotValid", page.KeyProblem);

        page.ProjectKey = "gm";
        Assert.False(page.HasKeyProblem);
        page.SaveCommand.Execute(null);
        Assert.True(page.IsEditing);
        Assert.Equal("Projects.KeyTaken(GM)", page.KeyProblem);
        Assert.Single(planner.Projects.All());

        page.ProjectKey = string.Empty;
        Assert.Equal("Projects.KeyNone", page.KeyHint);
        page.SaveCommand.Execute(null);
        Assert.False(page.IsEditing);
        Assert.Null(planner.Projects.Find("GoalMaker")!.ItemKey);
    }

    [Fact]
    public void EditingAProjectShowsItsKeyAndCanChangeOrClearIt()
    {
        var (page, project, _) = Board();

        page.EditCommand.Execute(null);
        Assert.Equal("GM", page.ProjectKey);
        page.ProjectKey = "goal";
        page.SaveCommand.Execute(null);
        Assert.Equal("GOAL", planner.Projects.Get(project.Id)!.ItemKey);
        Assert.Equal("GOAL-12", Card(page, "Ship the board").ItemId);

        page.EditCommand.Execute(null);
        page.ProjectKey = " ";
        page.SaveCommand.Execute(null);
        Assert.Null(planner.Projects.Get(project.Id)!.ItemKey);
        Assert.Equal("#12", Card(page, "Ship the board").ItemId);
    }

    [Fact]
    public void ACardShowsItsIdOnceTheServerHasNumberedIt()
    {
        var (page, project, _) = Board();
        page.NewItemTitle = "Write the docs";
        page.AddItemCommand.Execute(null);

        var numbered = Card(page, "Ship the board");
        Assert.Equal("GM-12", numbered.ItemId);
        Assert.Equal("GM-12 ", numbered.IdLead);
        Assert.Equal("Projects.ItemWithId(GM-12,Ship the board)", numbered.Name);

        // A new item shows no id until the synced row comes back with its number.
        var fresh = Card(page, "Write the docs");
        Assert.False(fresh.HasItemId);
        Assert.Equal(string.Empty, fresh.IdLead);
        Assert.Equal("Write the docs", fresh.Name);
        Assert.False(fresh.CopyIdCommand.CanExecute(null));

        Number(planner.Task("Write the docs").Id, 13);
        Assert.Equal("GM-13", Card(page, "Write the docs").ItemId);
        Assert.Equal(project.Id, planner.Task("Write the docs").ProjectId);
    }

    [Fact]
    public void CopyIdPutsTheIdOnTheClipboard()
    {
        var (page, _, item) = Board();

        Card(page, "Ship the board").CopyIdCommand.Execute(null);
        var detail = Detail(item.Id);
        detail.CopyIdCommand.Execute(null);

        Assert.Equal(["GM-12", "GM-12"], copied);
    }

    [Fact]
    public void TheItemsPageShowsItsIdAndReadsItWithTheTitle()
    {
        var (_, _, item) = Board();
        var plain = planner.Tasks.Add("Call the dentist")!;

        var detail = Detail(item.Id);
        Assert.Equal("GM-12", detail.ItemId);
        Assert.Equal("Task.TitleWithId(GM-12)", detail.TitleName);

        detail.Load(plain.Id, AppPage.Today);
        Assert.False(detail.HasItemId);
        Assert.Equal("Task.TitleLabel", detail.TitleName);
        Assert.False(detail.CopyIdCommand.CanExecute(null));
    }

    [Fact]
    public void AListChipCarriesTheIdAndSaysIt()
    {
        var (_, project, item) = Board();
        var projects = planner.Projects.All().ToDictionary(each => each.Id);

        var chip = ProjectTagViewModel.For(planner.Tasks.Find(item.Id)!, projects, planner.Strings)!;

        Assert.Equal("GM-12", chip.ItemId);
        Assert.Equal("Lists.ProjectWithId(Lists.ProjectTask(GoalMaker),GM-12)", chip.Label);
        Assert.Equal(project.Id, chip.ProjectId);
    }

    [Fact]
    public void TheBoardsSearchFindsAnItemByItsIdInAnyCaseOrByItsNumber()
    {
        var (page, _, _) = Board();
        page.NewItemTitle = "Write the docs";
        page.AddItemCommand.Execute(null);
        Number(planner.Task("Write the docs").Id, 13);

        foreach (var query in new[] { "GM-12", "gm-12", " #12 " })
        {
            page.BoardQuery = query;
            Assert.Equal(["Ship the board"], Titles(page));
        }

        page.BoardQuery = "JNT-12";
        Assert.Empty(Titles(page));
        page.BoardQuery = "docs";
        Assert.Equal(["Write the docs"], Titles(page));
        page.BoardQuery = string.Empty;
        Assert.Equal(2, Titles(page).Count);
    }

    [Fact]
    public void TheArchiveFindsADoneItemByItsId()
    {
        var (_, _, item) = Board();
        planner.Tasks.SetDone(item.Id, true);
        planner.Tasks.SetDone(planner.Tasks.Add("Ship the boat")!.Id, true);
        var archive = new ArchiveViewModel(planner.Tasks, planner.Areas, planner.Tags, planner.Projects, planner.Strings, _ => null, action => action(), _ => { });

        archive.Query = "gm-12";
        Assert.Equal(["Ship the board"], archive.Results.Select(row => row.Title));
        archive.Query = "#12";
        Assert.Empty(archive.Results);
        archive.Query = "ship";
        Assert.Equal(2, archive.Results.Count);
    }

    [Fact]
    public void GoToOpensAnItemByItsId()
    {
        var (_, _, item) = Board();
        var opened = new List<string>();
        var places = new PlacesViewModel(
            planner.Settings,
            planner.Strings,
            query => ProjectRules.ParseItemId(query) is { } wanted
                ? [.. ProjectRules.Named(wanted, planner.Tasks.All(), planner.Projects.All().ToDictionary(project => project.Id))
                    .Select(task => new PlaceEntry(task.Id, task.Title))]
                : [],
            opened.Add);
        places.OpenPaletteCommand.Execute(null);

        places.Query = "GM-12";
        Assert.Equal("Ship the board", places.Matches[0].Label);
        places.ChooseCommand.Execute(null);

        Assert.Equal([item.Id], opened);
        Assert.False(places.IsPaletteOpen);
    }

    // A GoalMaker board keyed GM with one item, Ship the board, that the server numbered 12.
    private (ProjectsViewModel Page, ProjectItem Project, TaskItem Item) Board()
    {
        var project = planner.Projects.Add(new ProjectDraft("GoalMaker"))!;
        planner.Projects.SetItemKey(project.Id, "GM");
        var item = planner.Tasks.Add("Ship the board")!;
        planner.Tasks.SetProject(item.Id, project.Id);
        Number(item.Id, 12);
        return (Page(), project, item);
    }

    // What a sync brings back: the row with the number the server gave it.
    private void Number(string taskId, int number)
    {
        var row = planner.Replica.Get("tasks", taskId)!;
        row["item_number"] = number;
        planner.Replica.Put("tasks", row);
    }

    private static BoardItemViewModel Card(ProjectsViewModel page, string title) =>
        page.Columns.SelectMany(column => column.Items).Single(card => card.Title == title);

    private static List<string> Titles(ProjectsViewModel page) =>
        [.. page.Columns.SelectMany(column => column.Items).Select(card => card.Title)];

    private TaskDetailViewModel Detail(string id)
    {
        var detail = new TaskDetailViewModel(
            planner.Tasks, planner.Areas, planner.Tags, planner.Steps, planner.Strings, planner.Time, action => action(), _ => { },
            planner.Goals, planner.Settings, planner.Projects, copied.Add);
        detail.Load(id, AppPage.Projects);
        return detail;
    }

    private ProjectsViewModel Page() =>
        new(planner.Projects, planner.Tasks, planner.Areas, planner.Tags, planner.Settings, planner.Strings, _ => null, _ => { }, action => action(), planner.Time, _ => { }, copied.Add);
}
