using GoalMaker.Core.Settings;

namespace GoalMaker.Core.Updates;

/// <summary>
/// The quiet check for updates: a little after the app starts and then about once a day while it
/// runs, so the mark on the way to Settings shows without the owner asking. It only checks and
/// verifies the signed manifest through <see cref="UpdateService"/>; downloading and installing
/// stay behind the owner's Install. A failure says nothing, and a build without a channel (or a dev
/// build) never checks. The last check that reached the channel is kept in the settings, so
/// restarts don't ask GitHub again within the day.
/// </summary>
public sealed class AutoUpdateCheck(UpdateService updates, ISettingsStore settings, TimeProvider time, TimeSpan interval) : IDisposable
{
    /// <summary>How often a running app looks: once a day.</summary>
    public static readonly TimeSpan Daily = TimeSpan.FromDays(1);

    private ITimer? timer;
    private int running;

    /// <summary>A check ran, the owner's or the quiet one, with what it found. May be raised off the UI thread.</summary>
    public event EventHandler<UpdateCheckResult>? Checked;

    /// <summary>When a check last reached the channel, or null before the first.</summary>
    public DateTimeOffset? LastChecked => settings.UpdatesCheckedAt;

    /// <summary>
    /// Whether the quiet check should run now: never without a channel; otherwise when no check has
    /// reached the channel for <c>interval</c> (or the clock went back past the last one), or when
    /// the last one found an update that this run of the app has not shown yet.
    /// </summary>
    public bool IsDue()
    {
        if (!updates.CanCheck)
        {
            return false;
        }

        if (settings.UpdatesCheckedAt is not { } last)
        {
            return true;
        }

        var now = time.GetUtcNow();
        if (now - last >= interval || now < last)
        {
            return true;
        }

        return settings.UpdateFound is not null && updates.Waiting is null;
    }

    /// <summary>
    /// The quiet check: runs when <see cref="IsDue"/> and no other quiet check is running, and
    /// returns what it found, or null when it did not run. It never throws for a failure.
    /// </summary>
    public async Task<UpdateCheckResult?> CheckIfDueAsync(CancellationToken cancellationToken)
    {
        if (!IsDue() || Interlocked.Exchange(ref running, 1) == 1)
        {
            return null;
        }

        try
        {
            return await CheckNowAsync(cancellationToken).ConfigureAwait(false);
        }
        catch (Exception error) when (error is not OperationCanceledException)
        {
            var failed = new UpdateCheckResult.Failed(error.Message);
            Checked?.Invoke(this, failed);
            return failed;
        }
        finally
        {
            Volatile.Write(ref running, 0);
        }
    }

    /// <summary>
    /// A check now, as Check for updates asks for. A check that reached the channel is written
    /// down with what it found; a failed one leaves the last time as it was, so the next tick tries again.
    /// </summary>
    public async Task<UpdateCheckResult> CheckNowAsync(CancellationToken cancellationToken)
    {
        var result = await updates.CheckAsync(cancellationToken).ConfigureAwait(false);
        if (result is UpdateCheckResult.UpToDate or UpdateCheckResult.Available or UpdateCheckResult.Untrusted)
        {
            settings.UpdateFound = (result as UpdateCheckResult.Available)?.Manifest.Version.ToString();
            settings.UpdatesCheckedAt = time.GetUtcNow();
        }

        Checked?.Invoke(this, result);
        return result;
    }

    /// <summary>
    /// Looks after <paramref name="firstDelay"/>, so the start is not held up, and then every
    /// <paramref name="every"/>; each look checks only when one is due. Nothing starts without a channel.
    /// </summary>
    public void Start(TimeSpan firstDelay, TimeSpan every)
    {
        if (!updates.CanCheck || timer is not null)
        {
            return;
        }

        timer = time.CreateTimer(_ => _ = CheckIfDueAsync(CancellationToken.None), null, firstDelay, every);
    }

    public void Dispose()
    {
        timer?.Dispose();
        timer = null;
    }
}
