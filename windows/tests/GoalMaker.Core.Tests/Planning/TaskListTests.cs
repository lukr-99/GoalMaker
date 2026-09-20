using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Tests.Sync;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.Core.Tests.Planning;

public sealed class TaskListTests : IDisposable
{
    private readonly TestReplica test = new();
    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 9, 18, 12, 0, 0, TimeSpan.Zero));
    private int syncRequests;

    private TaskList Tasks(string? owner = TestReplica.Owner)
    {
        var rows = new NewRows(test.Catalog, () => owner, time);
        var areas = new AreaList(test.Replica, rows, ["violet", "blue", "cyan"], () => syncRequests++);
        return new TaskList(
            test.Replica, rows, areas, new TagList(test.Replica, rows, () => syncRequests++), new ProjectList(test.Replica, rows, () => syncRequests++), () => syncRequests++, () => PlanningDay.Of(time.GetLocalNow().DateTime));
    }

    private static ComposerDraft Draft(string line) => ComposerParser.Parse(line, new DateTime(2026, 9, 18, 14, 5, 0));

    public void Dispose() => test.Dispose();

    [Fact]
    public void AddingQueuesACompleteRowAndAsksForASync()
    {
        var added = Tasks().Add("  Call the dentist  ");

        Assert.NotNull(added);
        Assert.Equal("Call the dentist", added.Title);
        var row = test.Replica.Get("tasks", added.Id)!;
        Assert.Equal(TestReplica.Owner, (string?)row["owner_id"]);
        Assert.Equal("2026-09-18T12:00:00.000000Z", (string?)row["created_at"]);
        Assert.Equal(test.Catalog["tasks"].Columns.Count, row.Count);

        // The server refuses a row that carries a null where its column is not null, and a null
        // defeats the column's default. RequiredColumnsTests walks every list this way.
        foreach (var column in test.Catalog["tasks"].Required)
        {
            Assert.True(row[column] is not null, $"{column} needs a value");
        }

        Assert.Single(test.Replica.Outbox());
        Assert.Equal(1, syncRequests);
    }

    [Fact]
    public void BlankTitlesAndSignedOutAddsAreIgnored()
    {
        Assert.Null(Tasks().Add("   "));
        Assert.Null(Tasks().Add(Draft("tomorrow #run")));
        Assert.Null(Tasks(owner: null).Add("Run"));
        Assert.Empty(test.Replica.Outbox());
    }

    [Fact]
    public void AComposerLineSavesItsDayTimePriorityAndRepeat()
    {
        var added = Tasks().Add(Draft("Standup weekdays 9:30 !"))!;

        var row = test.Replica.Get("tasks", added.Id)!;
        Assert.Equal("Standup", (string?)row["title"]);
        Assert.Equal("2026-09-21", (string?)row["planned_date"]);
        Assert.Equal("09:30:00", (string?)row["planned_time"]);
        Assert.True((bool)row["top_priority"]!);
        Assert.Equal("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", (string?)row["recurrence"]);
    }

    [Fact]
    public void ANewAreaGetsTheFirstUnusedPaletteColorAndIsSavedBeforeTheTask()
    {
        var tasks = Tasks();
        tasks.Add(Draft("Stretch @Health"));
        var second = tasks.Add(Draft("Read @School"))!;

        var areas = test.Replica.All("areas").ToDictionary(row => (string)row["name"]!, row => (string)row["color"]!);
        Assert.Equal(new Dictionary<string, string> { ["Health"] = "violet", ["School"] = "blue" }, areas);
        Assert.Equal(["areas", "tasks", "areas", "tasks"], test.Replica.Outbox().Select(entry => entry.Entity));
        Assert.Equal((string?)test.Replica.All("areas").Single(row => (string?)row["name"] == "School")["id"], second.AreaId);
    }

    [Fact]
    public void AnExistingAreaIsReusedWhateverItsCase()
    {
        var tasks = Tasks();
        var first = tasks.Add(Draft("Stretch @Health"))!;
        var second = tasks.Add(Draft("Run @health"))!;

        Assert.Single(test.Replica.All("areas"));
        Assert.Equal(first.AreaId, second.AreaId);
    }

    [Fact]
    public void AComposerLineFilesTheTaskIntoItsProject()
    {
        var tasks = Tasks();
        var projects = new ProjectList(test.Replica, new NewRows(test.Catalog, () => TestReplica.Owner, time), () => syncRequests++);
        projects.Add(new ProjectDraft("GoalMaker"));

        var item = tasks.Add(Draft("Ship the share target +goalmaker"))!;
        var idea = tasks.Add(Draft("Cache the manifest +GoalMaker ?"))!;

        Assert.Single(projects.All());
        Assert.Equal(projects.Find("GoalMaker")!.Id, item.ProjectId);
        Assert.Equal(ProjectRules.Task, item.ItemType);
        Assert.Equal(ProjectRules.Todo, item.BoardColumn);
        Assert.Equal(ProjectRules.Idea, idea.ItemType);
        Assert.Equal(ProjectRules.Backlog, idea.BoardColumn);
    }

    [Fact]
    public void AProjectTheLineNamesButNobodyMadeYetIsMadeWithIt()
    {
        var tasks = Tasks();
        var task = tasks.Add(Draft("Sign the installer +Signing"))!;

        var project = test.Replica.All("projects").Single();
        Assert.Equal("Signing", (string?)project["name"]);
        Assert.Equal((string?)project["id"], task.ProjectId);
        Assert.Equal(["projects", "tasks"], test.Replica.Outbox().Select(entry => entry.Entity));
    }

    [Fact]
    public void ATaskOutsideAProjectKeepsNoColumn()
    {
        var task = Tasks().Add(Draft("Call the dentist ?"))!;

        Assert.Null(task.BoardColumn);
        Assert.Equal(ProjectRules.Idea, task.ItemType);
    }

    [Fact]
    public void TagsAreCreatedOnceAndLinkedToTheTask()
    {
        var tasks = Tasks();
        var first = tasks.Add(Draft("Run #health #run"))!;
        tasks.Add(Draft("Swim #Health"));

        Assert.Equal(["health", "run"], test.Replica.All("tags").Select(row => (string)row["name"]!).Order(StringComparer.Ordinal));
        Assert.Equal(2, test.Replica.All("task_tags").Count(row => (string?)row["task_id"] == first.Id));
        Assert.Equal(3, test.Replica.All("task_tags").Count);
    }

    [Fact]
    public void DoneAndDeletedTasksLeaveTheOpenList()
    {
        var tasks = Tasks();
        var run = tasks.Add("Run")!;
        var read = tasks.Add("Read")!;
        time.Advance(TimeSpan.FromMinutes(1));

        tasks.SetDone(run.Id, true);
        tasks.Delete(read.Id);

        Assert.Empty(tasks.Open());
        var done = test.Replica.Get("tasks", run.Id)!;
        Assert.Equal("done", (string?)done["status"]);
        Assert.Equal("2026-09-18T12:01:00.000000Z", (string?)done["completed_at"]);
        Assert.Equal("2026-09-18T12:01:00.000000Z", (string?)test.Replica.Get("tasks", read.Id)!["deleted_at"]);
    }

    [Fact]
    public void ReopeningClearsTheCompletionTime()
    {
        var tasks = Tasks();
        var run = tasks.Add("Run")!;
        tasks.SetDone(run.Id, true);
        tasks.SetDone(run.Id, false);

        Assert.Single(tasks.Open());
        Assert.Null(test.Replica.Get("tasks", run.Id)!["completed_at"]);
    }

    [Fact]
    public void PlanningReopensATaskOnItsNewDayAndKeepsItsTime()
    {
        var tasks = Tasks();
        var call = tasks.Add(Draft("Call mum today 18:00"))!;
        tasks.SetDone(call.Id, true);
        tasks.Plan(call.Id, new DateOnly(2026, 9, 19));

        var row = test.Replica.Get("tasks", call.Id)!;
        Assert.Equal("2026-09-19", (string?)row["planned_date"]);
        Assert.Equal("18:00:00", (string?)row["planned_time"]);
        Assert.Equal("open", (string?)row["status"]);
        Assert.Null(row["completed_at"]);
    }

    [Fact]
    public void DroppingKeepsTheTaskButClosesIt()
    {
        var tasks = Tasks();
        var run = tasks.Add("Run")!;
        tasks.SetDone(run.Id, true);
        tasks.Drop(run.Id);

        var row = test.Replica.Get("tasks", run.Id)!;
        Assert.Equal("dropped", (string?)row["status"]);
        Assert.Null(row["completed_at"]);
        Assert.Null(row["deleted_at"]);
        Assert.Empty(tasks.Open());
        Assert.Equal(TaskState.Dropped, Assert.Single(tasks.All()).State);
    }

    [Fact]
    public void TopPriorityCanBeSetAndCleared()
    {
        var tasks = Tasks();
        var run = tasks.Add("Run")!;
        tasks.SetTopPriority(run.Id, true);
        Assert.True(Assert.Single(tasks.All()).TopPriority);
        tasks.SetTopPriority(run.Id, false);
        Assert.False(Assert.Single(tasks.All()).TopPriority);
    }

    [Fact]
    public void TheListAnnouncesChanges()
    {
        var tasks = Tasks();
        var changes = 0;
        tasks.Changed += (_, _) => changes++;

        tasks.Add("Run");

        Assert.Equal(1, changes);
    }
}
