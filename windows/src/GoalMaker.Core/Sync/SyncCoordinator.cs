using GoalMaker.Core.Auth;

namespace GoalMaker.Core.Sync;

/// <summary>
/// Runs sync when asked, one run at a time (docs/sync.md: "When sync runs"). <see cref="Request"/>
/// debounces bursts of local writes and Realtime events; a request during a run schedules exactly one
/// more run after it. While the server can't be reached it retries on its own, backing off from
/// <see cref="FirstRetry"/> to <see cref="LongestRetry"/>. Publishes the status the app's sync
/// indicator shows.
/// <para>
/// With <c>auth</c>, a run the server refuses the session for renews the session and runs once more.
/// A server that refuses the renewal, or the renewed session too, has ended it: the owner is sent to
/// sign in and the outbox stays in the replica for when they are back (docs/sign-in.md).
/// </para>
/// </summary>
public sealed class SyncCoordinator : IDisposable
{
    public static readonly TimeSpan FirstRetry = TimeSpan.FromSeconds(15);
    public static readonly TimeSpan LongestRetry = TimeSpan.FromMinutes(5);

    private readonly SyncEngine engine;
    private readonly IReplica replica;
    private readonly TimeProvider time;
    private readonly TimeSpan debounce;
    private readonly IAuthGateway? auth;
    private readonly SemaphoreSlim running = new(1, 1);
    private readonly Lock gate = new();
    private CancellationTokenSource? pendingRequest;
    private bool rerun;
    private TimeSpan nextRetry = FirstRetry;

    public SyncCoordinator(SyncEngine engine, IReplica replica, TimeProvider time, TimeSpan debounce, IAuthGateway? auth = null)
    {
        this.engine = engine;
        this.replica = replica;
        this.time = time;
        this.debounce = debounce;
        this.auth = auth;
        Status = SyncStatus.Initial with { PendingChanges = replica.PendingCount() };
    }

    public event EventHandler<SyncStatus>? StatusChanged;

    /// <summary>After each run, on the sync's thread, before the status is published (repeating tasks' repair).</summary>
    public event EventHandler<SyncReport>? RunCompleted;

    public SyncStatus Status { get; private set; }

    /// <summary>Asks for a sync soon; several requests within the debounce window make one run.</summary>
    public void Request() => Schedule(debounce);

    /// <summary>Drops the scheduled run and any offline retry, for sign-out.</summary>
    public void CancelScheduled()
    {
        lock (gate)
        {
            pendingRequest?.Cancel();
            pendingRequest?.Dispose();
            pendingRequest = null;
            nextRetry = FirstRetry;
        }
    }

    /// <summary>Syncs now. If a run is in progress, one more run follows it and this waits for both.</summary>
    public async Task<SyncReport> SyncNowAsync(CancellationToken cancellationToken)
    {
        if (!await running.WaitAsync(0, cancellationToken).ConfigureAwait(false))
        {
            lock (gate)
            {
                rerun = true;
            }

            await running.WaitAsync(cancellationToken).ConfigureAwait(false);
        }

        try
        {
            SyncReport report;
            var ended = false;
            do
            {
                lock (gate)
                {
                    rerun = false;
                }

                Publish(Status with { State = SyncState.Syncing });
                try
                {
                    report = await RunOnceAsync(cancellationToken).ConfigureAwait(false);
                    if (report.Unauthorized && auth is not null)
                    {
                        (report, ended) = await RenewAndRunAsync(auth, report, cancellationToken).ConfigureAwait(false);
                    }
                }
                catch (Exception error) when (error is not OperationCanceledException)
                {
                    // A bug or a broken replica must not take the app down; show it instead.
                    report = new SyncReport(0, 1, 0, Offline: false, Problem: error.Message);
                }

                var pending = replica.PendingCount();
                Publish(report switch
                {
                    { Offline: true } => Status with { State = SyncState.Offline, PendingChanges = pending, Problem = report.Problem },
                    { Rejected: > 0 } => new SyncStatus(SyncState.NeedsAttention, time.GetUtcNow(), pending, report.Problem),
                    _ => new SyncStatus(SyncState.Idle, time.GetUtcNow(), pending, null),
                });
            }
            while (!ended && ShouldRerun());

            // A session that ended waits for the owner to sign in again, not for a retry.
            if (!ended)
            {
                RetryIfOffline(report);
            }

            return report;
        }
        finally
        {
            running.Release();
        }
    }

    /// <summary>
    /// For sign-out: pushes what it can, then empties the replica. Returns false, and keeps the data,
    /// when changes are still unsynced and <paramref name="discardUnsynced"/> is false.
    /// </summary>
    public async Task<bool> FlushAndClearAsync(bool discardUnsynced, CancellationToken cancellationToken)
    {
        await SyncNowAsync(cancellationToken).ConfigureAwait(false);
        if (replica.PendingCount() > 0 && !discardUnsynced)
        {
            return false;
        }

        replica.ClearAll();
        CancelScheduled();
        Publish(SyncStatus.Initial);
        return true;
    }

    public void Dispose()
    {
        CancelScheduled();
        running.Dispose();
    }

    private async Task<SyncReport> RunOnceAsync(CancellationToken cancellationToken)
    {
        var report = await engine.RunAsync(cancellationToken).ConfigureAwait(false);
        RunCompleted?.Invoke(this, report);
        return report;
    }

    // A 401 is an expired token as often as an ended session, so the session is renewed and the run
    // goes once more. Offline, the usual retry picks it up. Returns whether the session ended.
    private async Task<(SyncReport Report, bool Ended)> RenewAndRunAsync(
        IAuthGateway gateway, SyncReport refused, CancellationToken cancellationToken)
    {
        var renewal = await gateway.RenewAsync(cancellationToken).ConfigureAwait(false);
        if (renewal != SessionRenewal.Renewed)
        {
            return (refused, renewal == SessionRenewal.Refused);
        }

        var again = await RunOnceAsync(cancellationToken).ConfigureAwait(false);
        if (!again.Unauthorized)
        {
            return (again, false);
        }

        await gateway.EndSessionAsync().ConfigureAwait(false);
        return (again, true);
    }

    // Replaces whatever run was scheduled: a newer request or retry always wins.
    private void Schedule(TimeSpan delay)
    {
        CancellationTokenSource request;
        lock (gate)
        {
            pendingRequest?.Cancel();
            pendingRequest?.Dispose();
            request = pendingRequest = new CancellationTokenSource();
        }

        _ = RunAfterDelayAsync(delay, request.Token);
    }

    private void RetryIfOffline(SyncReport report)
    {
        TimeSpan delay;
        lock (gate)
        {
            if (!report.Offline)
            {
                nextRetry = FirstRetry;
                return;
            }

            delay = nextRetry;
            nextRetry = TimeSpan.FromTicks(Math.Min(nextRetry.Ticks * 2, LongestRetry.Ticks));
        }

        Schedule(delay);
    }

    private async Task RunAfterDelayAsync(TimeSpan delay, CancellationToken requestToken)
    {
        try
        {
            await Task.Delay(delay, time, requestToken).ConfigureAwait(false);
            await SyncNowAsync(CancellationToken.None).ConfigureAwait(false);
        }
        catch (OperationCanceledException)
        {
            // A newer request replaced this one.
        }
        catch (ObjectDisposedException)
        {
            // The app is shutting down.
        }
    }

    private bool ShouldRerun()
    {
        lock (gate)
        {
            return rerun;
        }
    }

    private void Publish(SyncStatus status)
    {
        Status = status;
        StatusChanged?.Invoke(this, status);
    }
}
