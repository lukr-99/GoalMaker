using GoalMaker.App.Startup;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>The Windows task detail page and archive over a real replica (M2-12).</summary>
public sealed class TaskDetailViewModelTests : IDisposable
{
    private readonly TestPlanner planner = new();
    private readonly List<AppPage> opened = [];

    public void Dispose() => planner.Dispose();

    [Fact]
    public void FieldsSaveAsTheyChange()
    {
        var task = Add("Call the bank");
        var detail = Detail(task.Id);

        detail.Title = "Call the bank about the card";
        detail.PlannedDate = new DateTime(2026, 9, 21);
        detail.PlannedTimeText = "9:30";
        detail.Deadline = new DateTime(2026, 9, 25);
        detail.SelectedRepeat = detail.RepeatChoices.Single(choice => choice.Id == "FREQ=DAILY");

        var saved = planner.Tasks.Find(task.Id)!;
        Assert.Equal("Call the bank about the card", saved.Title);
        Assert.Equal((new DateOnly(2026, 9, 21), new TimeOnly(9, 30), new DateOnly(2026, 9, 25)), (saved.PlannedDate, saved.PlannedTime, saved.Deadline));
        Assert.Equal("FREQ=DAILY", saved.Recurrence);
    }

    [Fact]
    public void ARefusedValueGoesBackToWhatWasSaved()
    {
        var task = Add("Call the bank tomorrow 9:00");
        var detail = Detail(task.Id);

        detail.Title = "   ";
        detail.PlannedTimeText = "half past";

        Assert.Equal("Call the bank", detail.Title);
        Assert.Equal(new TimeOnly(9, 0), planner.Tasks.Find(task.Id)!.PlannedTime);
        Assert.Equal(new TimeOnly(9, 0).ToString("t", System.Globalization.CultureInfo.CurrentCulture), detail.PlannedTimeText);
    }

    [Fact]
    public void AnHourOnItsOwnIsATime()
    {
        var task = Add("Call the bank tomorrow");
        var detail = Detail(task.Id);

        detail.PlannedTimeText = "17";

        Assert.Equal(new TimeOnly(17, 0), planner.Tasks.Find(task.Id)!.PlannedTime);
    }

    [Fact]
    public void ClearingTheDayClearsTheTime()
    {
        var task = Add("Call the bank tomorrow 9:00");
        var detail = Detail(task.Id);

        detail.ClearDayCommand.Execute(null);

        Assert.Null(planner.Tasks.Find(task.Id)!.PlannedTime);
        Assert.False(detail.HasPlannedDate);
    }

    [Fact]
    public void NotesSaveWhenTheBoxIsLeft()
    {
        var task = Add("Book the trip");
        var detail = Detail(task.Id);

        detail.EditNotesCommand.Execute(null);
        detail.NotesDraft = "Check the **passport**";
        Assert.Equal(string.Empty, planner.Tasks.Find(task.Id)!.Notes);
        detail.FinishNotes();

        Assert.Equal("Check the **passport**", planner.Tasks.Find(task.Id)!.Notes);
        Assert.False(detail.IsEditingNotes);
        Assert.True(detail.HasNotes);
        Assert.True(detail.NotesSaved);
    }

    [Fact]
    public void NotesSaveAfterAPauseInTyping()
    {
        var task = Add("Book the trip");
        var detail = Detail(task.Id);

        detail.EditNotesCommand.Execute(null);
        detail.NotesDraft = "Check";
        planner.Time.Advance(TimeSpan.FromMilliseconds(500));
        detail.NotesDraft = "Check the passport";
        planner.Time.Advance(TimeSpan.FromMilliseconds(500));
        Assert.Equal(string.Empty, planner.Tasks.Find(task.Id)!.Notes);
        Assert.False(detail.NotesSaved);

        planner.Time.Advance(TimeSpan.FromMilliseconds(300));
        Assert.Equal("Check the passport", planner.Tasks.Find(task.Id)!.Notes);
        Assert.True(detail.NotesSaved);
        Assert.True(detail.IsEditingNotes);
    }

    [Fact]
    public void NotesSaveWhenThePageClosesOrAnotherTaskOpens()
    {
        var first = Add("Book the trip");
        var second = Add("Pack");
        var detail = Detail(first.Id);

        detail.EditNotesCommand.Execute(null);
        detail.NotesDraft = "Passport";
        detail.Load(second.Id, AppPage.Today);
        Assert.Equal("Passport", planner.Tasks.Find(first.Id)!.Notes);
        Assert.False(detail.IsEditingNotes);

        detail.EditNotesCommand.Execute(null);
        detail.NotesDraft = "Socks";
        detail.SaveNotes();
        Assert.Equal("Socks", planner.Tasks.Find(second.Id)!.Notes);

        detail.NotesDraft = "Socks and shoes";
        detail.BackCommand.Execute(null);
        Assert.Equal("Socks and shoes", planner.Tasks.Find(second.Id)!.Notes);
    }

    [Fact]
    public void NotesLeftAsTheyWereAreNotWritten()
    {
        var task = Add("Book the trip");
        planner.Tasks.SetNotes(task.Id, "Passport");
        var detail = Detail(task.Id);
        var writes = 0;
        planner.Tasks.Changed += (_, _) => writes++;

        detail.EditNotesCommand.Execute(null);
        detail.NotesDraft = "Passport!";
        detail.NotesDraft = "Passport";
        planner.Time.Advance(TimeSpan.FromSeconds(1));
        detail.FinishNotes();
        detail.SaveNotes();

        Assert.Equal(0, writes);
        Assert.False(detail.NotesSaved);
    }

    [Fact]
    public void AreaTagsAndStepsFromThePage()
    {
        var task = Add("Water plants #home");
        planner.Tags.FindOrCreate("weekend");
        planner.Areas.Create("Garden");
        var detail = Detail(task.Id);

        detail.SelectedArea = detail.AreaChoices.Single(choice => choice.Label == "Garden");
        detail.Tags.Single(tag => tag.Label == "#weekend").Command.Execute(null);
        detail.Tags.Single(tag => tag.Label == "#home").Command.Execute(null);
        detail.NewStepTitle = "Fill the can";
        detail.AddStepCommand.Execute(null);
        detail.Steps.Single().Done = true;

        Assert.Equal(planner.Areas.Find("Garden")!.Id, planner.Tasks.Find(task.Id)!.AreaId);
        Assert.Equal(["weekend"], planner.Tags.ForTask(task.Id).Select(tag => tag.Name));
        Assert.True(planner.Steps.ForTask(task.Id).Single().Done);
        Assert.Equal(string.Empty, detail.NewStepTitle);
    }

    [Fact]
    public void DeletingGoesBackToTheListItCameFrom()
    {
        var task = Add("Call the bank");
        var detail = Detail(task.Id, AppPage.Inbox);

        detail.DeleteCommand.Execute(null);

        Assert.Null(planner.Tasks.Find(task.Id));
        Assert.Equal([AppPage.Inbox], opened);
        Assert.False(detail.HasTask);
    }

    [Fact]
    public void TheArchiveSearchesAsYouTypeAndReopens()
    {
        var bank = Add("Call the bank");
        var milk = Add("Buy milk");
        planner.Tasks.SetDone(bank.Id, true);
        planner.Tasks.SetDone(milk.Id, true);
        var openedTasks = new List<string>();
        var archive = new ArchiveViewModel(planner.Tasks, planner.Areas, planner.Tags, planner.Projects, planner.Strings, _ => null, action => action(), openedTasks.Add);

        archive.Query = "bank";
        Assert.Equal(["Call the bank"], archive.Results.Select(row => row.Title));
        archive.Results.Single().OpenCommand.Execute(null);
        archive.Results.Single().ReopenCommand.Execute(null);

        Assert.Equal([bank.Id], openedTasks);
        Assert.Equal(TaskState.Open, planner.Tasks.Find(bank.Id)!.State);
        Assert.True(archive.IsEmpty);
        Assert.Equal("Archive.NoMatch", archive.EmptyText);
    }

    [Fact]
    public void ADoneProjectItemInTheArchiveWearsItsProjectsChip()
    {
        var item = Add("Fix the build +GoalMaker");
        var milk = Add("Buy milk");
        planner.Tasks.SetDone(item.Id, true);
        planner.Tasks.SetDone(milk.Id, true);
        var opened = new List<string>();
        var archive = new ArchiveViewModel(planner.Tasks, planner.Areas, planner.Tags, planner.Projects, planner.Strings, _ => null, action => action(), _ => { }, opened.Add);

        var row = archive.Results.Single(result => result.Title == "Fix the build");
        Assert.True(row.HasProject);
        Assert.Equal("Lists.ProjectTask(GoalMaker)", row.Project!.Label);
        Assert.False(archive.Results.Single(result => result.Title == "Buy milk").HasProject);
        row.Project.OpenCommand.Execute(null);
        Assert.Equal([planner.Projects.Find("GoalMaker")!.Id], opened);
    }

    [Fact]
    public void TheArchivesAreaAndTagFilterNarrowsWhatTheSearchFinds()
    {
        var shelf = Add("Fix the shelf @Home #errand");
        var invoice = Add("Send the invoice @Work");
        var fence = Add("Paint the fence");
        var house = planner.Projects.Add(new ProjectDraft("House") { AreaId = planner.Areas.Find("Home")!.Id })!;
        planner.Tasks.SetProject(fence.Id, house.Id, ProjectRules.Task);
        foreach (var task in new[] { shelf, invoice, fence })
        {
            planner.Tasks.SetDone(task.Id, true);
        }

        var archive = new ArchiveViewModel(planner.Tasks, planner.Areas, planner.Tags, planner.Projects, planner.Strings, _ => null, action => action(), _ => { });

        archive.Filters.SelectedArea = archive.Filters.AreaChoices.Single(area => area.Label == "Home");
        Assert.Equal(["Fix the shelf", "Paint the fence"], archive.Results.Select(row => row.Title).Order());

        archive.Filters.SelectedTag = archive.Filters.TagChoices.Single(tag => tag.Label == "#errand");
        Assert.Equal(["Fix the shelf"], archive.Results.Select(row => row.Title));

        archive.Query = "invoice";
        Assert.True(archive.IsEmpty);
        Assert.Equal("Archive.NoMatch", archive.EmptyText);
    }

    [Fact]
    public void TheGoalPickerOffersOpenGoalsStillRunningAndLinksOne()
    {
        var task = Add("Long run");
        var week = planner.Goals.Add(new GoalDraft("3 runs", GoalHorizon.Week, new DateOnly(2026, 9, 14), GoalRules.ModeTasks))!;
        planner.Goals.Add(new GoalDraft("Last week", GoalHorizon.Week, new DateOnly(2026, 9, 7)));
        var done = planner.Goals.Add(new GoalDraft("Book the race", GoalHorizon.Month, new DateOnly(2026, 9, 1)))!;
        planner.Goals.SetStatus(done.Id, GoalRules.Done);
        planner.Goals.Add(new GoalDraft("Half marathon", GoalHorizon.Year, new DateOnly(2026, 1, 1)));
        var detail = Detail(task.Id);

        Assert.Equal(["Task.NoGoal", "Half marathon · Goals.HorizonYear", "3 runs · Goals.HorizonWeek"], detail.GoalChoices.Select(choice => choice.Label));

        detail.SelectedGoal = detail.GoalChoices.Single(choice => choice.Id == week.Id);
        Assert.Equal(week.Id, planner.Tasks.Find(task.Id)!.GoalId);
        detail.SelectedGoal = detail.GoalChoices[0];
        Assert.Null(planner.Tasks.Find(task.Id)!.GoalId);
    }

    private TaskDetailViewModel Detail(string id, AppPage from = AppPage.Today)
    {
        var detail = new TaskDetailViewModel(
            planner.Tasks, planner.Areas, planner.Tags, planner.Steps, planner.Strings, planner.Time, action => action(), opened.Add, planner.Goals, planner.Settings);
        detail.Load(id, from);
        return detail;
    }

    private TaskItem Add(string line)
    {
        var task = planner.Tasks.Add(ComposerParser.Parse(line, planner.Time.GetLocalNow().DateTime))!;
        planner.Time.Advance(TimeSpan.FromSeconds(1));
        return task;
    }
}
