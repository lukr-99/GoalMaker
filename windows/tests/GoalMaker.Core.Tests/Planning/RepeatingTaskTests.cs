using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Sync;
using GoalMaker.Core.Tests.Sync;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>Repeating tasks on one device and across two (docs/repeating.md).</summary>
public sealed class RepeatingTaskTests : IDisposable
{
    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 9, 18, 12, 0, 0, TimeSpan.Zero));
    private readonly FakeServer server = new();
    private readonly Device phone;
    private readonly Device pc;

    public RepeatingTaskTests()
    {
        time.SetLocalTimeZone(TimeZoneInfo.Utc);
        phone = new Device(time, server);
        pc = new Device(time, server);
    }

    public void Dispose()
    {
        phone.Dispose();
        pc.Dispose();
    }

    [Fact]
    public void FinishingMakesTheNextOccurrenceWithTheSamePlan()
    {
        var run = phone.Add("Run daily 19:00 #health @Home !");

        phone.Tasks.SetDone(run.Id, true);

        var next = phone.Task(Occurrences.SuccessorId(run.Id));
        Assert.Equal(new DateOnly(2026, 9, 19), next.PlannedDate);
        Assert.Equal(new TimeOnly(19, 0), next.PlannedTime);
        Assert.Equal(("Run", TaskState.Open, true), (next.Title, next.State, next.TopPriority));
        Assert.Equal(run.AreaId, next.AreaId);
        Assert.Equal("FREQ=DAILY", next.Recurrence);
        Assert.Equal(run.Id, run.SeriesId);
        Assert.Equal(run.Id, next.SeriesId);
        var link = phone.Replica.Replica.Get("task_tags", Occurrences.TagLinkId(next.Id, phone.TagLinks(run.Id).Single()))!;
        Assert.Equal(next.Id, (string?)link["task_id"]);
    }

    [Fact]
    public void DroppingMovesOnToo()
    {
        var review = phone.Add("Review budget every friday");

        phone.Tasks.Drop(review.Id);

        Assert.Equal(new DateOnly(2026, 9, 25), phone.Task(Occurrences.SuccessorId(review.Id)).PlannedDate);
    }

    [Fact]
    public void ReopeningTakesTheNextOccurrenceBackAndFinishingAgainBringsItBack()
    {
        var run = phone.Add("Run daily");
        var nextId = Occurrences.SuccessorId(run.Id);

        phone.Tasks.SetDone(run.Id, true);
        phone.Tasks.SetDone(run.Id, false);
        Assert.Equal([run.Id], phone.Tasks.Open().Select(task => task.Id));
        Assert.NotNull(phone.Replica.Replica.Get("tasks", nextId)![SyncedTable.DeletedAt]);

        phone.Tasks.Drop(run.Id);
        Assert.Equal([nextId], phone.Tasks.Open().Select(task => task.Id));

        phone.Tasks.Plan(run.Id, new DateOnly(2026, 9, 19));
        Assert.Equal([run.Id], phone.Tasks.Open().Select(task => task.Id));
    }

    [Fact]
    public void ChangingHowItFinishedDoesntMakeASecondOccurrence()
    {
        var run = phone.Add("Run daily");

        phone.Tasks.SetDone(run.Id, true);
        phone.Tasks.Drop(run.Id);

        Assert.Single(phone.Tasks.Open());
        Assert.Equal(2, phone.Tasks.All().Count);
    }

    [Fact]
    public void TasksThatDontRepeatJustFinish()
    {
        var call = phone.Add("Call the bank");
        var odd = phone.Add("Stretch");
        var row = phone.Replica.Replica.Get("tasks", odd.Id)!;
        row["recurrence"] = "FREQ=YEARLY";
        phone.Replica.Replica.Queue("tasks", row);

        phone.Tasks.SetDone(call.Id, true);
        phone.Tasks.SetDone(odd.Id, true);

        Assert.Empty(phone.Tasks.Open());
        Assert.Equal(2, phone.Tasks.All().Count);
    }

    [Fact]
    public void BothDevicesFinishingTheSameOccurrenceMakeOneNext()
    {
        var run = phone.Add("Run daily");
        phone.Sync();
        pc.Sync();

        phone.Tasks.SetDone(run.Id, true);
        pc.Tasks.Drop(run.Id);
        phone.Sync();
        pc.Sync();
        phone.Sync();

        Assert.Equal([Occurrences.SuccessorId(run.Id)], phone.OpenIds());
        Assert.Equal(phone.OpenIds(), pc.OpenIds());
    }

    [Fact]
    public void AnOfflineDeviceNeverLeavesTwoOpenOccurrences()
    {
        var run = phone.Add("Run daily");
        phone.Sync();
        pc.Sync();

        // The phone finishes two days in a row while the PC is offline and finishes the first again.
        phone.Tasks.SetDone(run.Id, true);
        var second = Occurrences.SuccessorId(run.Id);
        phone.Tasks.SetDone(second, true);
        var third = Occurrences.SuccessorId(second);
        phone.Sync();
        pc.Tasks.SetDone(run.Id, true);

        pc.Sync();
        phone.Sync();
        pc.Sync();

        Assert.Equal([third], phone.OpenIds());
        Assert.Equal([third], pc.OpenIds());
        Assert.Equal([third], server.Rows("tasks")
            .Where(row => (string?)row["status"] == "open" && row[SyncedTable.DeletedAt] is null)
            .Select(row => (string)row["id"]!));
        Assert.Equal(TaskState.Dropped, pc.Task(second).State);
    }

    /// <summary>A device: its own replica and task list, syncing with the shared fake server.</summary>
    private sealed class Device : IDisposable
    {
        private readonly FakeTimeProvider time;
        private readonly SyncEngine engine;

        public Device(FakeTimeProvider time, FakeServer server)
        {
            this.time = time;
            var rows = new NewRows(Replica.Catalog, () => TestReplica.Owner, time);
            var areas = new AreaList(Replica.Replica, rows, ["violet", "blue"], () => { });
            Tasks = new TaskList(Replica.Replica, rows, areas, new TagList(Replica.Replica, rows, () => { }), new ProjectList(Replica.Replica, rows, () => { }), () => { }, () => PlanningDay.Of(time.GetLocalNow().DateTime));
            engine = new SyncEngine(Replica.Catalog, Replica.Replica, server, time);
        }

        public TestReplica Replica { get; } = new();

        public TaskList Tasks { get; }

        public TaskItem Add(string line)
        {
            var task = Tasks.Add(ComposerParser.Parse(line, time.GetLocalNow().DateTime))!;
            time.Advance(TimeSpan.FromSeconds(1));
            return Tasks.All().Single(item => item.Id == task.Id);
        }

        public TaskItem Task(string id) => Tasks.All().Single(task => task.Id == id);

        public IReadOnlyList<string> TagLinks(string taskId) =>
            [.. Replica.Replica.All("task_tags").Where(link => (string?)link["task_id"] == taskId).Select(link => (string)link["tag_id"]!)];

        public IReadOnlyList<string> OpenIds() => [.. Tasks.Open().Select(task => task.Id)];

        // What the apps do: run, repair when rows came in, and push the repair.
        public void Sync()
        {
            var report = engine.RunAsync(CancellationToken.None).GetAwaiter().GetResult();
            Assert.Equal(0, report.Rejected);
            if (report.Pulled > 0 && Tasks.RepairSeries())
            {
                engine.RunAsync(CancellationToken.None).GetAwaiter().GetResult();
            }

            time.Advance(TimeSpan.FromSeconds(1));
        }

        public void Dispose() => Replica.Dispose();
    }
}
