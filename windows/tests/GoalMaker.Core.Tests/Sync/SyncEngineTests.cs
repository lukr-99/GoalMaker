using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;
using GoalMaker.Infrastructure.Sync;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.Core.Tests.Sync;

public sealed class SyncEngineTests : IDisposable
{
    private readonly TestReplica test = new();
    private readonly FakeServer server = new();
    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 9, 18, 12, 0, 0, TimeSpan.Zero));

    private SyncEngine Engine() => new(test.Catalog, test.Replica, server, time);

    private CancellationToken Token => TestContext.Current.CancellationToken;

    public void Dispose() => test.Dispose();

    [Fact]
    public async Task ALocalTaskReachesTheServerAndComesBackStamped()
    {
        test.Replica.Queue("tasks", test.NewTask("a", "Run"));

        var report = await Engine().RunAsync(Token);

        Assert.Equal(1, report.Pushed);
        Assert.Empty(test.Replica.Outbox());
        var local = test.Replica.Get("tasks", "a")!;
        Assert.Matches(@"^2026-09-18T10:00:00\.[0-9]{6}Z$", (string)local["updated_at"]!);
        Assert.Equal("Run", (string?)server.Rows("tasks").Single()["title"]);
    }

    [Fact]
    public async Task ADevBuildsLocalOnlySyncKeepsItsRowsRunAfterRun()
    {
        // Pulling from a remote that never sends anything back would be a full resync every run,
        // clearing the replica; a local-only engine only pushes (docs/sign-in.md).
        var local = new SyncEngine(test.Catalog, test.Replica, new LocalOnlyRemoteTables(time), time, pulls: false);
        test.Replica.Queue("tasks", test.NewTask("a", "Run"));

        await local.RunAsync(Token);
        var report = await local.RunAsync(Token);

        Assert.Empty(test.Replica.Outbox());
        Assert.Equal(0, report.Pulled);
        Assert.Equal("Run", (string?)test.Replica.Get("tasks", "a")!["title"]);
    }

    [Fact]
    public async Task RowsFromAnotherDeviceArrive()
    {
        server.Seed("tasks", test.NewTask("remote", "From the phone"));

        var report = await Engine().RunAsync(Token);

        Assert.Equal(1, report.Pulled);
        Assert.Equal("From the phone", (string?)test.Replica.Get("tasks", "remote")!["title"]);
        Assert.NotNull(test.Replica.Watermark("tasks"));
    }

    [Fact]
    public async Task ManyRowsArriveAcrossPages()
    {
        for (var index = 0; index < SyncEngine.PageSize * 2 + 17; index++)
        {
            server.Seed("tasks", test.NewTask($"t{index:D5}", $"Task {index}"));
        }

        await Engine().RunAsync(Token);

        Assert.Equal(SyncEngine.PageSize * 2 + 17, test.Replica.All("tasks").Count);
    }

    [Fact]
    public async Task ASecondSyncOnlyFetchesTheOverlap()
    {
        server.Seed("tasks", test.NewTask("a", "One"));
        await Engine().RunAsync(Token);
        server.Advance(TimeSpan.FromMinutes(5));
        server.Seed("tasks", test.NewTask("b", "Two"));

        var report = await Engine().RunAsync(Token);

        // "a" again (inside the 60-second overlap, merged as a no-op) and the new "b".
        Assert.Equal(2, report.Pulled);
        Assert.NotNull(test.Replica.Get("tasks", "b"));
    }

    [Fact]
    public async Task APendingLocalEditIsNotOverwrittenByAPull()
    {
        var row = test.NewTask("a", "Server title");
        server.Seed("tasks", row);
        await Engine().RunAsync(Token);
        server.Offline = true;
        var local = test.Replica.Get("tasks", "a")!;
        local["title"] = "Local title";
        test.Replica.Queue("tasks", local);
        row["title"] = "Changed on the phone";
        server.Offline = false;
        server.Seed("tasks", row);
        server.RefusedIds.Add("a");

        await Engine().RunAsync(Token);

        Assert.Equal("Local title", (string?)test.Replica.Get("tasks", "a")!["title"]);
        Assert.Single(test.Replica.Outbox());
    }

    [Fact]
    public async Task ADeletionElsewhereBeatsAPendingEditHere()
    {
        var row = test.NewTask("a", "Run");
        server.Seed("tasks", row);
        await Engine().RunAsync(Token);
        var local = test.Replica.Get("tasks", "a")!;
        local["title"] = "Run 5 km";
        test.Replica.Queue("tasks", local);
        row["deleted_at"] = "2026-09-18T11:00:00.000000Z";
        server.Seed("tasks", row);
        server.RefusedIds.Add("a");

        await Engine().RunAsync(Token);

        Assert.NotNull(test.Replica.Get("tasks", "a")!["deleted_at"]);
        Assert.Empty(test.Replica.Outbox());
    }

    [Fact]
    public async Task ARefusedRowIsRecordedAndTheRestStillSync()
    {
        test.Replica.Queue("tasks", test.NewTask("bad", "Refused"));
        test.Replica.Queue("tasks", test.NewTask("good", "Accepted"));
        server.RefusedIds.Add("bad");

        var report = await Engine().RunAsync(Token);

        Assert.Equal(1, report.Pushed);
        Assert.Equal(1, report.Rejected);
        var left = Assert.Single(test.Replica.Outbox());
        Assert.Equal("bad", left.RowId);
        Assert.Equal(1, left.Attempts);
        Assert.Contains("403", left.LastError, StringComparison.Ordinal);
    }

    [Fact]
    public async Task OfflineKeepsEverythingForLater()
    {
        test.Replica.Queue("tasks", test.NewTask("a", "Run"));
        server.Offline = true;

        var report = await Engine().RunAsync(Token);

        Assert.True(report.Offline);
        Assert.Single(test.Replica.Outbox());
        Assert.Equal(0, report.Pushed);
    }

    [Fact]
    public async Task AStaleDeviceStartsOverAndDropsRowsPurgedMeanwhile()
    {
        server.Seed("tasks", test.NewTask("kept", "Still there"));
        await Engine().RunAsync(Token);
        test.Replica.Put("tasks", test.NewTask("purged", "Deleted and purged while this device was away"));
        test.Replica.SetWatermark("tasks", "2026-06-01T00:00:00.000000Z");

        await Engine().RunAsync(Token);

        Assert.Null(test.Replica.Get("tasks", "purged"));
        Assert.NotNull(test.Replica.Get("tasks", "kept"));
    }

    [Fact]
    public async Task ServerTimestampsAreStoredNormalized()
    {
        var row = test.NewTask("a", "Done");
        row["status"] = "done";
        row["completed_at"] = "2026-09-18T12:30:00+02:00";
        server.Seed("tasks", row);

        await Engine().RunAsync(Token);

        Assert.Equal("2026-09-18T10:30:00.000000Z", (string?)test.Replica.Get("tasks", "a")!["completed_at"]);
    }

    [Fact]
    public void NormalizeKeepsEveryDescribedColumnAndNothingElse()
    {
        var row = test.NewTask("a", "Run");
        row["surprise"] = "from a newer server";

        var normalized = SyncEngine.Normalize(test.Catalog["tasks"], row);

        Assert.False(normalized.ContainsKey("surprise"));
        Assert.Equal(test.Catalog["tasks"].Columns.Count, normalized.Count);
        Assert.IsType<JsonObject>(normalized);
    }
}
