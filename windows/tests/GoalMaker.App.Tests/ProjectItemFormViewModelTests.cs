using System.Windows.Input;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>
/// The new item window of the Projects page over a real replica: what opens it and with what, the
/// column following the type, the title it needs, its keys, Add another, and the item it makes.
/// </summary>
public sealed class ProjectItemFormViewModelTests : IDisposable
{
    private readonly TestPlanner planner = new();

    // The new item windows the page asked for, as the forms they would show.
    private readonly List<ProjectItemFormViewModel> windows = [];

    public void Dispose() => planner.Dispose();

    [Fact]
    public void TheWindowOpensOnlyForAProjectOnShow()
    {
        var page = Page();

        Assert.False(page.OpenItemWindowCommand.CanExecute(null));
        page.OpenItemWindowCommand.Execute(null);
        Assert.Empty(windows);

        var form = Open(WithProject());
        Assert.Equal("ProjectItem.Heading(GoalMaker)", form.Heading);
        Assert.Equal(string.Empty, form.Title);
        Assert.Equal(ProjectRules.Task, form.ItemType);
        Assert.Equal(ProjectRules.Todo, form.Column);
        Assert.Equal(ProjectRules.Normal, form.Priority);
        Assert.Null(form.Milestone.Id);
        Assert.False(form.HasMilestones);
        Assert.False(form.AddAnother);
    }

    [Fact]
    public void TheColumnFollowsTheTypeUntilOneIsPicked()
    {
        var form = Open(WithProject());

        form.ItemType = ProjectRules.Idea;
        Assert.Equal(ProjectRules.Backlog, form.Column);
        form.ItemType = ProjectRules.Bug;
        Assert.Equal(ProjectRules.Todo, form.Column);

        form.Column = ProjectRules.Doing;
        form.ItemType = ProjectRules.Idea;
        Assert.Equal(ProjectRules.Doing, form.Column);
    }

    [Fact]
    public void AColumnsPlusOpensTheWindowInThatColumnAndDoneHasNone()
    {
        var page = WithProject();

        Assert.False(Column(page, ProjectRules.Done).CanAdd);
        Assert.True(Column(page, ProjectRules.Doing).CanAdd);
        Assert.Equal("Projects.AddTo(Projects.Doing)", Column(page, ProjectRules.Doing).AddText);
        Column(page, ProjectRules.Doing).AddCommand.Execute(null);

        var form = Assert.Single(windows);
        Assert.Equal(ProjectRules.Doing, form.Column);
        form.ItemType = ProjectRules.Idea;
        Assert.Equal(ProjectRules.Doing, form.Column);
    }

    [Fact]
    public void ATitleIsNeeded()
    {
        var form = Open(WithProject());
        var finished = 0;
        var titleWanted = 0;
        form.Finished += (_, _) => finished++;
        form.TitleWanted += (_, _) => titleWanted++;

        form.Title = "   ";
        form.Notes = "Only notes";
        form.CreateCommand.Execute(null);

        Assert.True(form.TitleMissing);
        Assert.Equal(1, titleWanted);
        Assert.Equal(0, finished);
        Assert.Equal(0, form.Added);
        Assert.Empty(planner.Tasks.All());

        form.Title = "Undo on the board";
        Assert.False(form.TitleMissing);
    }

    [Fact]
    public void EnterInTheTitleAddsTheItemAndCloses()
    {
        var form = Open(WithProject());
        var finished = 0;
        form.Finished += (_, _) => finished++;
        form.Title = "Undo on the board";

        Assert.True(form.Press(Key.Enter, ModifierKeys.None, inTitle: true));

        Assert.Equal(1, finished);
        Assert.Equal(1, form.Added);
        Assert.Equal(ProjectRules.Todo, planner.Task("Undo on the board").BoardColumn);
    }

    [Fact]
    public void EnterInTheNotesIsANewLineAndCtrlEnterAddsTheItem()
    {
        var form = Open(WithProject());
        var finished = 0;
        form.Finished += (_, _) => finished++;
        form.Title = "Undo on the board";

        Assert.False(form.Press(Key.Enter, ModifierKeys.None, inTitle: false));
        Assert.False(form.Press(Key.Enter, ModifierKeys.Shift, inTitle: true));
        Assert.Empty(planner.Tasks.All());

        Assert.True(form.Press(Key.Enter, ModifierKeys.Control, inTitle: false));
        Assert.Equal(1, finished);
        Assert.Single(planner.Tasks.All());
    }

    [Fact]
    public void EscapeClosesWithoutAdding()
    {
        var form = Open(WithProject());
        var finished = 0;
        form.Finished += (_, _) => finished++;
        form.Title = "Undo on the board";

        Assert.False(form.Press(Key.A, ModifierKeys.None, inTitle: true));
        Assert.True(form.Press(Key.Escape, ModifierKeys.None, inTitle: true));

        Assert.Equal(1, finished);
        Assert.Empty(planner.Tasks.All());
    }

    [Fact]
    public void AddAnotherKeepsTheWindowOpenAndClearsTheTitle()
    {
        var page = WithProject();
        var form = Open(page);
        var finished = 0;
        var titleWanted = 0;
        form.Finished += (_, _) => finished++;
        form.TitleWanted += (_, _) => titleWanted++;
        form.AddAnother = true;
        form.ItemType = ProjectRules.Bug;
        form.Priority = ProjectRules.High;
        form.Title = "Undo on the board";
        form.Notes = "Like the lists have";

        form.Press(Key.Enter, ModifierKeys.None, inTitle: true);

        Assert.Equal(0, finished);
        Assert.Equal(1, titleWanted);
        Assert.Equal(string.Empty, form.Title);
        Assert.Equal(string.Empty, form.Notes);
        Assert.False(form.TitleMissing);
        Assert.True(form.HasAdded);
        Assert.Equal("ProjectItem.Added(Undo on the board)", form.AddedText);
        Assert.Equal(ProjectRules.Bug, form.ItemType);
        Assert.Equal(ProjectRules.High, form.Priority);

        form.Title = "Drag cards";
        form.Press(Key.Enter, ModifierKeys.Control, inTitle: true);

        Assert.Equal(2, form.Added);
        Assert.Equal(["Drag cards", "Undo on the board"], Column(page, ProjectRules.Todo).Items.Select(item => item.Title).Order());
        Assert.All(Column(page, ProjectRules.Todo).Items, item => Assert.Equal("Projects.Bug", item.Type));

        // The next window opens as this one was left.
        form.Cancel();
        Assert.Equal(1, finished);
        Assert.True(Open(page).AddAnother);
    }

    [Fact]
    public void TheItemTakesEveryFieldOfTheForm()
    {
        var page = WithProject();
        var project = planner.Projects.All()[0];
        planner.Projects.AddMilestone(project.Id, "M1");
        var milestone = planner.Projects.AddMilestone(project.Id, "M2")!;
        var form = Open(page);

        Assert.True(form.HasMilestones);
        Assert.Equal(["ProjectItem.NoMilestone", "M1", "M2"], form.Milestones.Select(choice => choice.Label));
        form.Title = "  Undo on the board  ";
        form.ItemType = ProjectRules.Bug;
        form.Column = ProjectRules.Doing;
        form.Priority = ProjectRules.Urgent;
        form.Milestone = form.Milestones.Single(choice => choice.Label == "M2");
        form.PlannedDay = new DateTime(2026, 9, 21);
        form.Deadline = new DateTime(2026, 9, 30);
        form.Notes = "Like the lists have:\n\n- **five** seconds\n";
        form.CreateCommand.Execute(null);

        var item = planner.Task("Undo on the board");
        Assert.Equal(project.Id, item.ProjectId);
        Assert.Equal(ProjectRules.Bug, item.ItemType);
        Assert.Equal(ProjectRules.Doing, item.BoardColumn);
        Assert.Equal(ProjectRules.Urgent, item.Priority);
        Assert.Equal(milestone.Id, item.MilestoneId);
        Assert.Equal(new DateOnly(2026, 9, 21), item.PlannedDate);
        Assert.Equal(new DateOnly(2026, 9, 30), item.Deadline);
        Assert.Equal("Like the lists have:\n\n- **five** seconds", item.Notes);
        Assert.Equal(ProjectRules.Owner, item.MadeBy);
        Assert.Equal(TaskState.Open, item.State);
        Assert.Equal(["Undo on the board"], Column(page, ProjectRules.Doing).Items.Select(card => card.Title));
    }

    [Fact]
    public void AnItemWithoutTheExtrasHasNoDayDeadlineOrMilestone()
    {
        var form = Open(WithProject());
        form.ItemType = ProjectRules.Idea;
        form.Title = "Widgets for habits";

        form.CreateCommand.Execute(null);

        var item = planner.Task("Widgets for habits");
        Assert.Equal(ProjectRules.Backlog, item.BoardColumn);
        Assert.Equal(ProjectRules.Normal, item.Priority);
        Assert.Null(item.PlannedDate);
        Assert.Null(item.Deadline);
        Assert.Null(item.MilestoneId);
        Assert.Equal(string.Empty, item.Notes);
    }

    [Fact]
    public void TheQuickLineMovesIntoTheWindowAndLeavesOnceAdded()
    {
        var page = WithProject();
        page.NewItemType = ProjectRules.Idea;
        page.NewItemTitle = "Widgets for habits";

        var cancelled = Open(page);
        Assert.Equal("Widgets for habits", cancelled.Title);
        Assert.Equal(ProjectRules.Idea, cancelled.ItemType);
        Assert.Equal(ProjectRules.Backlog, cancelled.Column);
        cancelled.Cancel();
        Assert.Equal("Widgets for habits", page.NewItemTitle);

        var form = Open(page);
        form.Title = "Widgets for habits and goals";
        form.Create();

        Assert.Equal(string.Empty, page.NewItemTitle);
        Assert.Equal(ProjectRules.Idea, planner.Task("Widgets for habits and goals").ItemType);
    }

    private ProjectItemFormViewModel Open(ProjectsViewModel page)
    {
        page.OpenItemWindowCommand.Execute(null);
        return windows[^1];
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
        new(planner.Projects, planner.Tasks, planner.Settings, planner.Strings, _ => { }, action => action(), planner.Time, windows.Add);
}
