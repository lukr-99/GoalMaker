using GoalMaker.Core.Sync;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.Core.Tests.Sync;

public sealed class SyncCoordinatorTests : IDisposable
{
    private readonly TestReplica test = new();
    private readonly FakeServer server = new();
    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 9, 18, 12, 0, 0, TimeSpan.Zero));

    private SyncCoordinator Coordinator() =>
        new(new SyncEngine(test.Catalog, test.Replica, server, time), test.Replica, time, TimeSpan.FromSeconds(2));

    private CancellationToken Token => TestContext.Current.CancellationToken;

    public void Dispose() => test.Dispose();

    [Fact]
    public async Task ABurstOfRequestsBecomesOneSync()
    {
        using var coordinator = Coordinator();
        var runs = 0;
        coordinator.StatusChanged += (_, status) =>
        {
            if (status.State == SyncState.Syncing)
            {
                Interlocked.Increment(ref runs);
            }
        };
        test.Replica.Queue("tasks", test.NewTask("a", "Run"));

        coordinator.Request();
        coordinator.Request();
        coordinator.Request();
        time.Advance(TimeSpan.FromSeconds(1));
        Assert.Equal(0, runs);
        time.Advance(TimeSpan.FromSeconds(2));
        await WaitUntil(() => coordinator.Status.State == SyncState.Idle && coordinator.Status.LastSyncedAt is not null);

        Assert.Equal(1, runs);
        Assert.Equal(1, server.Upserts);
    }

    [Fact]
    public async Task OfflineIsShownWithTheWaitingChanges()
    {
        using var coordinator = Coordinator();
        test.Replica.Queue("tasks", test.NewTask("a", "Run"));
        server.Offline = true;

        await coordinator.SyncNowAsync(Token);

        Assert.Equal(SyncState.Offline, coordinator.Status.State);
        Assert.Equal(1, coordinator.Status.PendingChanges);
    }

    [Fact]
    public async Task OfflineRetriesOnItsOwnAndBacksOff()
    {
        using var coordinator = Coordinator();
        var runs = 0;
        coordinator.StatusChanged += (_, status) =>
        {
            if (status.State == SyncState.Syncing)
            {
                Interlocked.Increment(ref runs);
            }
        };
        test.Replica.Queue("tasks", test.NewTask("a", "Run"));
        server.Offline = true;

        await coordinator.SyncNowAsync(Token);
        time.Advance(SyncCoordinator.FirstRetry - TimeSpan.FromSeconds(1));
        Assert.Equal(1, runs);
        time.Advance(TimeSpan.FromSeconds(1));
        await WaitUntil(() => runs == 2 && coordinator.Status.State == SyncState.Offline);

        // The second retry waits twice as long; the server is back by then.
        server.Offline = false;
        time.Advance(SyncCoordinator.FirstRetry);
        Assert.Equal(2, runs);
        time.Advance(SyncCoordinator.FirstRetry);
        await WaitUntil(() => coordinator.Status.State == SyncState.Idle && coordinator.Status.PendingChanges == 0);

        Assert.Single(server.Rows("tasks"));
    }

    [Fact]
    public async Task CancelScheduledStopsTheOfflineRetry()
    {
        using var coordinator = Coordinator();
        server.Offline = true;
        await coordinator.SyncNowAsync(Token);

        coordinator.CancelScheduled();
        server.Offline = false;
        time.Advance(SyncCoordinator.LongestRetry);
        await Task.Delay(50, Token);

        Assert.Equal(SyncState.Offline, coordinator.Status.State);
    }

    [Fact]
    public async Task SignOutKeepsUnsyncedChangesUnlessToldOtherwise()
    {
        using var coordinator = Coordinator();
        test.Replica.Queue("tasks", test.NewTask("a", "Run"));
        server.Offline = true;

        Assert.False(await coordinator.FlushAndClearAsync(discardUnsynced: false, Token));
        Assert.Single(test.Replica.Outbox());

        Assert.True(await coordinator.FlushAndClearAsync(discardUnsynced: true, Token));
        Assert.Empty(test.Replica.All("tasks"));
    }

    [Fact]
    public async Task SignOutAfterASuccessfulPushClearsEverything()
    {
        using var coordinator = Coordinator();
        test.Replica.Queue("tasks", test.NewTask("a", "Run"));

        Assert.True(await coordinator.FlushAndClearAsync(discardUnsynced: false, Token));

        Assert.Single(server.Rows("tasks"));
        Assert.Empty(test.Replica.All("tasks"));
    }

    private static async Task WaitUntil(Func<bool> condition)
    {
        for (var attempt = 0; attempt < 200 && !condition(); attempt++)
        {
            await Task.Delay(10);
        }

        Assert.True(condition());
    }
}
