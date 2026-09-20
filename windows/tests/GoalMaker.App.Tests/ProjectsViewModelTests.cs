using GoalMaker.App.ViewModels;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>The Windows projects page over a real replica: the board and what moving a card does (M5-02).</summary>
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
        var row = Assert.Single(page.Projects);
        Assert.Equal("GoalMaker", row.Name);
        Assert.Equal("Projects.Active", row.Status);
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

    private static BoardColumnViewModel Column(ProjectsViewModel page, string column) =>
        page.Columns.Single(candidate => candidate.Column == column);

    private ProjectsViewModel WithProject()
    {
        var page = Page();
        page.NewCommand.Execute(null);
        page.ProjectName = "GoalMaker";
        page.SaveCommand.Execute(null);
        return page;
    }

    private ProjectsViewModel Page() =>
        new(planner.Projects, planner.Tasks, planner.Strings, _ => { }, action => action());
}
