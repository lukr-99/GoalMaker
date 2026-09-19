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
    public void NotesAreEditedAsTextAndSavedOnRequest()
    {
        var task = Add("Book the trip");
        var detail = Detail(task.Id);

        detail.EditNotesCommand.Execute(null);
        detail.NotesDraft = "Check the **passport**";
        Assert.Equal(string.Empty, planner.Tasks.Find(task.Id)!.Notes);
        detail.SaveNotesCommand.Execute(null);

        Assert.Equal("Check the **passport**", planner.Tasks.Find(task.Id)!.Notes);
        Assert.False(detail.IsEditingNotes);
        Assert.True(detail.HasNotes);
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
        var archive = new ArchiveViewModel(planner.Tasks, planner.Strings, action => action(), openedTasks.Add);

        archive.Query = "bank";
        Assert.Equal(["Call the bank"], archive.Results.Select(row => row.Title));
        archive.Results.Single().OpenCommand.Execute(null);
        archive.Results.Single().ReopenCommand.Execute(null);

        Assert.Equal([bank.Id], openedTasks);
        Assert.Equal(TaskState.Open, planner.Tasks.Find(bank.Id)!.State);
        Assert.True(archive.IsEmpty);
        Assert.Equal("Archive.NoMatch", archive.EmptyText);
    }

    private TaskDetailViewModel Detail(string id, AppPage from = AppPage.Today)
    {
        var detail = new TaskDetailViewModel(planner.Tasks, planner.Areas, planner.Tags, planner.Steps, planner.Strings, planner.Time, action => action(), opened.Add);
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
