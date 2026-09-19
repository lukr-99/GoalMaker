using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>Every field of a task's detail view, its checklist, and the archive, on a real replica (M2-12).</summary>
public sealed class TaskDetailsTests : IDisposable
{
    private readonly TestPlanner planner = new();

    public void Dispose() => planner.Dispose();

    [Fact]
    public void TitleAndNotes()
    {
        var task = Add("Book the trip");

        Assert.True(planner.Tasks.Rename(task.Id, "  Book the train  "));
        Assert.False(planner.Tasks.Rename(task.Id, "   "));
        planner.Tasks.SetNotes(task.Id, "Check the **passport**");

        var saved = planner.Tasks.Find(task.Id)!;
        Assert.Equal("Book the train", saved.Title);
        Assert.Equal("Check the **passport**", saved.Notes);
    }

    [Fact]
    public void ATimeNeedsADayAndADeadlineIsItsOwnDate()
    {
        var task = Add("Call the bank");

        planner.Tasks.Schedule(task.Id, new DateOnly(2026, 9, 21), new TimeOnly(9, 30));
        planner.Tasks.SetDeadline(task.Id, new DateOnly(2026, 9, 25));
        var saved = planner.Tasks.Find(task.Id)!;
        Assert.Equal((new DateOnly(2026, 9, 21), new TimeOnly(9, 30), new DateOnly(2026, 9, 25)), (saved.PlannedDate, saved.PlannedTime, saved.Deadline));

        planner.Tasks.Schedule(task.Id, null, new TimeOnly(10, 0));
        saved = planner.Tasks.Find(task.Id)!;
        Assert.Equal(((DateOnly?)null, (TimeOnly?)null, new DateOnly(2026, 9, 25)), (saved.PlannedDate, saved.PlannedTime, saved.Deadline));
    }

    [Fact]
    public void AreaTagsAndRepeat()
    {
        var task = Add("Water plants #home");
        var garden = planner.Areas.Create("Garden")!;

        planner.Tasks.SetArea(task.Id, garden.Id);
        planner.Tasks.SetTags(task.Id, ["weekend", "home"]);
        Assert.True(planner.Tasks.SetRecurrence(task.Id, "FREQ=WEEKLY;BYDAY=SA"));
        Assert.False(planner.Tasks.SetRecurrence(task.Id, "FREQ=YEARLY"));

        var saved = planner.Tasks.Find(task.Id)!;
        Assert.Equal(garden.Id, saved.AreaId);
        Assert.Equal(["home", "weekend"], planner.Tags.ForTask(task.Id).Select(tag => tag.Name).Order(StringComparer.Ordinal));
        Assert.Equal(("FREQ=WEEKLY;BYDAY=SA", task.Id), (saved.Recurrence, saved.SeriesId));

        planner.Tasks.SetTags(task.Id, ["weekend"]);
        planner.Tasks.SetRecurrence(task.Id, null);
        Assert.Equal(["weekend"], planner.Tags.ForTask(task.Id).Select(tag => tag.Name));
        Assert.Null(planner.Tasks.Find(task.Id)!.Recurrence);
    }

    [Fact]
    public void AChecklistKeepsItsOrderAndStepsAreCheckedRenamedMovedAndDeleted()
    {
        var task = Add("Pack for the trip");
        var steps = planner.Steps;
        var socks = steps.Add(task.Id, "Socks")!;
        steps.Add(task.Id, "Charger");
        var passport = steps.Add(task.Id, "Passport")!;
        Assert.Null(steps.Add(task.Id, "  "));

        steps.SetDone(socks.Id, true);
        Assert.True(steps.Rename(passport.Id, "Passport and tickets"));
        steps.Move(passport.Id, 0);
        Assert.Equal(["Passport and tickets", "Socks", "Charger"], steps.ForTask(task.Id).Select(step => step.Title));
        Assert.True(steps.ForTask(task.Id).Single(step => step.Id == socks.Id).Done);

        steps.Delete(socks.Id);
        Assert.Equal(["Passport and tickets", "Charger"], steps.ForTask(task.Id).Select(step => step.Title));
    }

    [Fact]
    public void TheArchiveFindsADoneTaskByAWordOfItsTitleAndReopensIt()
    {
        var bank = Add("Call the bank");
        var milk = Add("Buy milk");
        planner.Tasks.SetDone(bank.Id, true);
        planner.Time.Advance(TimeSpan.FromHours(1));
        planner.Tasks.SetDone(milk.Id, true);

        Assert.Equal(["Buy milk", "Call the bank"], ArchiveRules.Search(planner.Tasks.All(), string.Empty).Select(task => task.Title));
        Assert.Equal([bank.Id], ArchiveRules.Search(planner.Tasks.All(), "bank").Select(task => task.Id));

        planner.Tasks.SetDone(bank.Id, false);
        Assert.Equal([milk.Id], ArchiveRules.Search(planner.Tasks.All(), string.Empty).Select(task => task.Id));
        Assert.Equal(TaskState.Open, planner.Tasks.Find(bank.Id)!.State);
    }

    private TaskItem Add(string line)
    {
        var task = planner.Tasks.Add(ComposerParser.Parse(line, planner.Time.GetLocalNow().DateTime))!;
        planner.Time.Advance(TimeSpan.FromSeconds(1));
        return task;
    }
}
