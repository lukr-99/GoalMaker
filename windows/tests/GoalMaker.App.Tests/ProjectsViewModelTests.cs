using System.Globalization;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>
/// The Windows projects page over a real replica: the board, what moving a card does, and taking it back
/// (M5-02); done items leaving the board and folded columns.
/// </summary>
public sealed class ProjectsViewModelTests : IDisposable
{
    private readonly TestPlanner planner = new();

    public void Dispose() => planner.Dispose();

    [Fact]
    public void WithNoProjectsThePageSaysSo()
    {
        var page = Page();

        Assert.True(page.IsEmpty);
        Assert.False(page.HasProject);
        Assert.False(page.ShowsProject);
        Assert.Equal(["backlog", "todo", "doing", "done"], page.Columns.Select(column => column.Column));
    }

    [Fact]
    public void ANewProjectIsSavedAndShown()
    {
        var page = Page();

        page.NewCommand.Execute(null);
        page.ProjectName = "GoalMaker";
        page.ProjectRepository = "https://github.com/owner/goalmaker";
        page.SaveCommand.Execute(null);

        Assert.False(page.IsEmpty);
        Assert.True(page.HasProject);
        Assert.True(page.ShowsProject);
        var row = Assert.Single(page.Projects);
        Assert.Equal("GoalMaker", row.Name);
        Assert.Equal("Projects.Active", row.Status);
        Assert.Equal(ProjectRules.Active, row.StatusId);
        Assert.Equal("https://github.com/owner/goalmaker", planner.Projects.All()[0].RepositoryUrl);
    }

    [Fact]
    public void AnIdeaLandsInTheBacklogAndATaskInToDo()
    {
        var page = WithProject();

        page.NewItemType = ProjectRules.Idea;
        page.NewItemTitle = "Widgets for habits";
        page.AddItemCommand.Execute(null);
        page.NewItemType = ProjectRules.Task;
        page.NewItemTitle = "Ship the board";
        page.AddItemCommand.Execute(null);

        Assert.Equal(["Widgets for habits"], Column(page, "backlog").Items.Select(item => item.Title));
        Assert.Equal(["Ship the board"], Column(page, "todo").Items.Select(item => item.Title));
        Assert.Equal("Projects.Idea", Column(page, "backlog").Items[0].Type);
        Assert.Equal(string.Empty, page.NewItemTitle);
    }

    [Fact]
    public void ANewItemKeepsTheColumnPriorityAndNotesItWasGiven()
    {
        var page = WithProject();

        page.NewItemType = ProjectRules.Idea;
        Assert.Equal(ProjectRules.Backlog, page.NewItemColumn);
        page.NewItemColumn = ProjectRules.Doing;
        page.NewItemType = ProjectRules.Bug;
        page.NewItemPriority = ProjectRules.High;
        page.NewItemNotes = "Like the lists have";
        page.NewItemTitle = "Undo on the board";
        page.AddItemCommand.Execute(null);

        var item = planner.Task("Undo on the board");
        Assert.Equal(ProjectRules.Bug, item.ItemType);
        Assert.Equal(ProjectRules.Doing, item.BoardColumn);
        Assert.Equal(ProjectRules.High, item.Priority);
        Assert.Equal("Like the lists have", item.Notes);
        Assert.Equal(string.Empty, page.NewItemNotes);
    }

    [Fact]
    public void UndoingAMoveToDonePutsTheCardBackOpen()
    {
        var page = WithProject();
        page.NewItemTitle = "Ship the board";
        page.AddItemCommand.Execute(null);

        Column(page, "todo").Items[0].MoveCommand.Execute("done");
        Assert.True(page.HasUndo);
        Assert.Equal("Lists.Done(Ship the board)", page.UndoText);
        page.UndoCommand.Execute(null);

        Assert.False(page.HasUndo);
        Assert.Equal(ProjectRules.Todo, planner.Task("Ship the board").BoardColumn);
        Assert.Equal(TaskState.Open, planner.Task("Ship the board").State);
    }

    [Fact]
    public void UndoingTakingAnItemOutPutsItBackWhereItWas()
    {
        var page = WithProject();
        page.NewItemType = ProjectRules.Bug;
        page.NewItemColumn = ProjectRules.Doing;
        page.NewItemTitle = "Cache the release feed";
        page.AddItemCommand.Execute(null);

        Column(page, "doing").Items[0].RemoveCommand.Execute(null);
        Assert.Null(planner.Task("Cache the release feed").ProjectId);
        page.UndoCommand.Execute(null);

        var item = planner.Task("Cache the release feed");
        Assert.Equal(planner.Projects.All()[0].Id, item.ProjectId);
        Assert.Equal(ProjectRules.Bug, item.ItemType);
        Assert.Equal(ProjectRules.Doing, item.BoardColumn);
    }

    [Fact]
    public void TheUndoBarGoesAfterFiveSeconds()
    {
        var page = WithProject();
        page.NewItemTitle = "Ship the board";
        page.AddItemCommand.Execute(null);

        Column(page, "todo").Items[0].MoveCommand.Execute("done");
        planner.Time.Advance(TimeSpan.FromSeconds(5));

        Assert.False(page.HasUndo);
    }

    [Fact]
    public void MovingACardToDoneFinishesTheTask()
    {
        var page = WithProject();
        page.NewItemTitle = "Ship the board";
        page.AddItemCommand.Execute(null);

        Column(page, "todo").Items[0].MoveCommand.Execute("done");

        Assert.True(Column(page, "todo").IsEmpty);
        var card = Assert.Single(Column(page, "done").Items);
        Assert.Equal("Ship the board", card.Title);
        Assert.Equal(TaskState.Done, planner.Task("Ship the board").State);
    }

    [Fact]
    public void AnItemTakenOutOfTheProjectStaysATask()
    {
        var page = WithProject();
        page.NewItemTitle = "Ship the board";
        page.AddItemCommand.Execute(null);

        Column(page, "todo").Items[0].RemoveCommand.Execute(null);

        Assert.True(Column(page, "todo").IsEmpty);
        Assert.Null(planner.Task("Ship the board").ProjectId);
        Assert.Null(planner.Task("Ship the board").BoardColumn);
    }

    [Fact]
    public void ADeletedProjectLeavesItsItemsBehind()
    {
        var page = WithProject();
        page.NewItemTitle = "Ship the board";
        page.AddItemCommand.Execute(null);

        page.EditCommand.Execute(null);
        page.DeleteCommand.Execute(null);

        Assert.True(page.IsEmpty);
        Assert.Equal("Ship the board", planner.Task("Ship the board").Title);
    }

    [Fact]
    public void TheSwitchShowsEveryoneOrOnlyTheOwnersOrOnlyClaudesItems()
    {
        var page = WithProject();
        page.NewItemTitle = "Ship the board";
        page.AddItemCommand.Execute(null);
        page.NewItemTitle = "Cache the release feed";
        page.AddItemCommand.Execute(null);

        // Claude made this one through the connector, and that is how it arrives from the server.
        var row = planner.Replica.Get("tasks", planner.Task("Cache the release feed").Id)!;
        row["made_by"] = ProjectRules.Claude;
        planner.Replica.Put("tasks", row);
        page.Refresh();

        Assert.Equal(
            ["Projects.MadeByAll", "Projects.MadeByOwner", "Projects.MadeByClaude"],
            page.MakerFilters.Select(choice => choice.Label));
        Assert.Equal(ProjectRules.Everyone, page.MadeByFilter);
        Assert.Equal(2, Column(page, "todo").Items.Count);
        Assert.True(Column(page, "todo").Items.Single(item => item.Title == "Cache the release feed").MadeByClaude);
        Assert.False(Column(page, "todo").Items.Single(item => item.Title == "Ship the board").MadeByClaude);

        page.MadeByFilter = ProjectRules.Owner;
        Assert.Equal(["Ship the board"], Column(page, "todo").Items.Select(item => item.Title));

        page.MadeByFilter = ProjectRules.Claude;
        Assert.Equal(["Cache the release feed"], Column(page, "todo").Items.Select(item => item.Title));
    }

    [Fact]
    public void ADoneItemLeavesTheBoardFourteenPlanningDaysAfterItWasFinished()
    {
        var page = WithProject();
        foreach (var title in new[] { "Old news", "Last week", "Just in" })
        {
            page.NewItemTitle = title;
            page.AddItemCommand.Execute(null);
            planner.Tasks.SetBoardColumn(planner.Task(title).Id, ProjectRules.Done);
        }

        // Today is Friday 18 September. 02:00 on the 5th still belongs to the 4th, which is 14 days ago;
        // 05:00 on the 5th is the 5th, 13 days ago.
        Finished("Old news", "2026-09-05T02:00:00.000000Z");
        Finished("Last week", "2026-09-05T05:00:00.000000Z");
        page.Refresh();

        var done = Column(page, "done");
        Assert.Equal(["Just in", "Last week"], done.Items.Select(item => item.Title).Order());
        Assert.True(done.HasArchived);
        Assert.Equal("Projects.ArchivedCount(1)", done.ArchivedText);
        var archived = Assert.Single(done.Archived);
        Assert.Equal("Old news", archived.Title);
        Assert.Equal($"Archive.DoneOn({new DateOnly(2026, 9, 4).ToString("d MMM", CultureInfo.CurrentCulture)})", archived.DoneText);
        Assert.False(Column(page, "todo").HasArchived);
    }

    [Fact]
    public void AnItemTooOldForDoneIsReopenedToGoBack()
    {
        var page = WithProject();
        page.NewItemTitle = "Old news";
        page.AddItemCommand.Execute(null);
        planner.Tasks.SetBoardColumn(planner.Task("Old news").Id, ProjectRules.Done);
        Finished("Old news", "2026-08-01T10:00:00.000000Z");
        page.Refresh();

        var archived = Assert.Single(Column(page, "done").Archived);
        Assert.Equal("Projects.Reopen", archived.ActionText);
        archived.PutBackCommand.Execute(null);

        Assert.Equal(["Old news"], Column(page, "todo").Items.Select(item => item.Title));
        Assert.False(Column(page, "done").HasArchived);
        Assert.Equal(TaskState.Open, planner.Task("Old news").State);
    }

    [Fact]
    public void ADoneItemCanBeArchivedByHandAndPutBack()
    {
        var page = WithProject();
        page.NewItemTitle = "Ship the board";
        page.AddItemCommand.Execute(null);
        Assert.False(Column(page, "todo").Items[0].CanArchive);
        Column(page, "todo").Items[0].MoveCommand.Execute("done");

        var card = Assert.Single(Column(page, "done").Items);
        Assert.True(card.CanArchive);
        card.ArchiveCommand.Execute(null);

        Assert.True(Column(page, "done").IsEmpty);
        Assert.Equal("Projects.ArchivedItem(Ship the board)", page.UndoText);
        Assert.NotNull(planner.Task("Ship the board").BoardArchivedAt);
        Assert.Equal(TaskState.Done, planner.Task("Ship the board").State);

        var archived = Assert.Single(Column(page, "done").Archived);
        Assert.Equal("Projects.PutBack", archived.ActionText);
        archived.PutBackCommand.Execute(null);

        Assert.Equal(["Ship the board"], Column(page, "done").Items.Select(item => item.Title));
        Assert.Null(planner.Task("Ship the board").BoardArchivedAt);
    }

    [Fact]
    public void UndoingAnArchivePutsTheItemBackInDone()
    {
        var page = WithProject();
        page.NewItemTitle = "Ship the board";
        page.AddItemCommand.Execute(null);
        Column(page, "todo").Items[0].MoveCommand.Execute("done");

        Column(page, "done").Items[0].ArchiveCommand.Execute(null);
        page.UndoCommand.Execute(null);

        Assert.Single(Column(page, "done").Items);
        Assert.False(Column(page, "done").HasArchived);
    }

    [Fact]
    public void TheProjectSaysHowLongDoneItemsStay()
    {
        var page = WithProject();
        page.NewItemTitle = "Old news";
        page.AddItemCommand.Execute(null);
        planner.Tasks.SetBoardColumn(planner.Task("Old news").Id, ProjectRules.Done);
        Finished("Old news", "2026-08-01T10:00:00.000000Z");
        page.Refresh();

        Assert.Equal("14", page.ProjectArchiveAfter);
        Assert.Equal(
            ["Projects.ArchiveDays(7)", "Projects.ArchiveDays(14)", "Projects.ArchiveDays(30)", "Projects.ArchiveDays(90)", "Projects.ArchiveNever"],
            page.ArchiveChoices.Select(choice => choice.Label));

        page.EditCommand.Execute(null);
        page.ProjectArchiveAfter = "never";
        page.SaveCommand.Execute(null);

        Assert.Null(planner.Projects.All()[0].ArchiveAfterDays);
        Assert.Equal(["Old news"], Column(page, "done").Items.Select(item => item.Title));
        Assert.False(Column(page, "done").HasArchived);

        page.EditCommand.Execute(null);
        page.ProjectArchiveAfter = "90";
        page.SaveCommand.Execute(null);
        Assert.Equal(90, planner.Projects.All()[0].ArchiveAfterDays);
    }

    [Fact]
    public void ANumberOfDaysSetElsewhereStaysInThePicker()
    {
        var page = WithProject();
        planner.Projects.SetArchiveAfterDays(planner.Projects.All()[0].Id, 1);
        page.Refresh();

        Assert.Equal("1", page.ProjectArchiveAfter);
        Assert.Equal("Projects.ArchiveDay(1)", page.ArchiveChoices[0].Label);
    }

    [Fact]
    public void AFoldedColumnIsRememberedOnEveryBoard()
    {
        var page = WithProject();
        Assert.Equal(840, page.BoardMinWidth);
        var done = Column(page, "done");
        Assert.True(done.IsUnfolded);

        done.FoldCommand.Execute(null);

        Assert.True(done.IsFolded);
        Assert.False(done.IsUnfolded);
        Assert.Equal(["done"], planner.Settings.FoldedBoardColumns);
        Assert.Equal(678, page.BoardMinWidth);
        Assert.Equal("Projects.Unfold(Projects.Done)", done.UnfoldText);

        var again = Page();
        Assert.True(Column(again, "done").IsFolded);
        Assert.False(Column(again, "todo").IsFolded);

        Column(again, "done").FoldCommand.Execute(null);
        Assert.Empty(planner.Settings.FoldedBoardColumns);
    }

    [Fact]
    public void AFoldedColumnStillCountsItsCards()
    {
        var page = WithProject();
        page.NewItemTitle = "Ship the board";
        page.AddItemCommand.Execute(null);

        Column(page, "todo").FoldCommand.Execute(null);

        Assert.Equal(1, Column(page, "todo").Count);
    }

    // As the server stamps it: when the item was finished.
    private void Finished(string title, string at)
    {
        var row = planner.Replica.Get("tasks", planner.Task(title).Id)!;
        row["completed_at"] = at;
        planner.Replica.Put("tasks", row);
    }

    private static BoardColumnViewModel Column(ProjectsViewModel page, string column) =>
        page.Columns.Single(candidate => candidate.Column == column);

    [Fact]
    public void AnAreaKeepsItsProjectsAndAnItemWithoutOneTakesItsProjects()
    {
        var page = TwoAreas();

        page.Filters.SelectedArea = page.Filters.AreaChoices.Single(area => area.Label == "Work");

        Assert.Equal(["GoalMaker"], page.Projects.Select(project => project.Name));
        Assert.Equal(["Ship the board"], Column(page, "todo").Items.Select(item => item.Title));
    }

    [Fact]
    public void AnItemsOwnAreaKeepsItsProjectListedUnderThatArea()
    {
        var page = TwoAreas();

        page.Filters.SelectedArea = page.Filters.AreaChoices.Single(area => area.Label == "Home");
        page.Select(page.Projects.Single(project => project.Name == "GoalMaker").Id);

        Assert.Equal(["GoalMaker", "House"], page.Projects.Select(project => project.Name).Order());
        Assert.Equal(["Order a shelf"], Column(page, "todo").Items.Select(item => item.Title));
    }

    [Fact]
    public void ATagKeepsTheProjectsHoldingATaggedItem()
    {
        var page = TwoAreas();

        page.Filters.SelectedTag = page.Filters.TagChoices.Single(tag => tag.Label == "#errand");

        Assert.Equal(["House"], page.Projects.Select(project => project.Name));
        Assert.Equal(["Buy paint"], Column(page, "todo").Items.Select(item => item.Title));

        page.Filters.ClearCommand.Execute(null);
        Assert.Equal(2, page.Projects.Count);
    }

    [Fact]
    public void AFilterThatHidesEveryProjectSaysSo()
    {
        var page = TwoAreas();
        planner.Areas.FindOrCreate("Garden");

        page.Filters.SelectedArea = page.Filters.AreaChoices.Single(area => area.Label == "Garden");

        Assert.Empty(page.Projects);
        Assert.False(page.HasProject);
        Assert.True(page.IsFilteredAway);
        Assert.False(page.IsEmpty);
    }

    [Fact]
    public void EditingAProjectKeepsItsNotesAndSetsItsArea()
    {
        var work = planner.Areas.FindOrCreate("Work")!;
        var project = planner.Projects.Add(new ProjectDraft("GoalMaker") { Notes = "Ship by spring." })!;
        var page = Page();

        page.EditCommand.Execute(null);
        page.ProjectArea = page.ProjectAreaChoices.Single(choice => choice.Id == work.Id);
        page.SaveCommand.Execute(null);

        Assert.Equal("Ship by spring.", planner.Projects.Get(project.Id)!.Notes);
        Assert.Equal(work.Id, planner.Projects.Get(project.Id)!.AreaId);
    }

    // A Work project with an item of its own and one filed under Home, and a Home project with an errand.
    private ProjectsViewModel TwoAreas()
    {
        var work = planner.Areas.FindOrCreate("Work")!;
        var home = planner.Areas.FindOrCreate("Home")!;
        var goalMaker = planner.Projects.Add(new ProjectDraft("GoalMaker") { AreaId = work.Id })!;
        var house = planner.Projects.Add(new ProjectDraft("House") { AreaId = home.Id })!;
        TaskItem Item(string title, string project)
        {
            var task = planner.Tasks.Add(title)!;
            planner.Tasks.SetProject(task.Id, project, ProjectRules.Task);
            return task;
        }

        Item("Ship the board", goalMaker.Id);
        planner.Tasks.SetArea(Item("Order a shelf", goalMaker.Id).Id, home.Id);
        planner.Tasks.SetTags(Item("Buy paint", house.Id).Id, ["errand"]);
        return Page();
    }

    private ProjectsViewModel WithProject()
    {
        var page = Page();
        page.NewCommand.Execute(null);
        page.ProjectName = "GoalMaker";
        page.SaveCommand.Execute(null);
        return page;
    }

    private ProjectsViewModel Page() =>
        new(planner.Projects, planner.Tasks, planner.Areas, planner.Tags, planner.Settings, planner.Strings, _ => null, _ => { }, action => action(), planner.Time);
}
