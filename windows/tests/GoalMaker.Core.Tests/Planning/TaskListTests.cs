using GoalMaker.Core.Planning;
using GoalMaker.Core.Tests.Sync;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.Core.Tests.Planning;

public sealed class TaskListTests : IDisposable
{
    private readonly TestReplica test = new();
    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 9, 18, 12, 0, 0, TimeSpan.Zero));
    private int syncRequests;

    private TaskList Tasks(string? owner = TestReplica.Owner) =>
        new(test.Catalog, test.Replica, () => owner, time, () => syncRequests++);

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
        Assert.Single(test.Replica.Outbox());
        Assert.Equal(1, syncRequests);
    }

    [Fact]
    public void BlankTitlesAndSignedOutAddsAreIgnored()
    {
        Assert.Null(Tasks().Add("   "));
        Assert.Null(Tasks(owner: null).Add("Run"));
        Assert.Empty(test.Replica.Outbox());
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
    public void TheListAnnouncesChanges()
    {
        var tasks = Tasks();
        var changes = 0;
        tasks.Changed += (_, _) => changes++;

        tasks.Add("Run");

        Assert.Equal(1, changes);
    }
}
