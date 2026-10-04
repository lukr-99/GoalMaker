using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Planning;

/// <summary>
/// Life goal pictures as files (docs/life-goals.md, ADR 0018): a picture is kept here at once and goes
/// up when the app can reach the server; a picture this device lacks comes down once; a deleted
/// picture's file goes a day after its row did, so an undo in between still finds it.
/// </summary>
public sealed class LifeGoalPictures
{
    private static readonly TimeSpan Grace = TimeSpan.FromDays(1);
    private readonly LifeGoalList lifeGoals;
    private readonly IPictureFiles files;
    private readonly IPictureCloud cloud;
    private readonly Func<string?> owner;
    private readonly TimeProvider time;
    private readonly Action requestTransfer;
    private readonly SemaphoreSlim running = new(1, 1);
    private int version;

    /// <param name="owner">The signed-in owner, or null when nothing may be sent (signed out, or a dev build kept on this PC).</param>
    /// <param name="requestTransfer">Asks for a <see cref="TransferAsync"/> soon, after a picture was added.</param>
    public LifeGoalPictures(
        LifeGoalList lifeGoals, IPictureFiles files, IPictureCloud cloud, Func<string?> owner, TimeProvider time, Action requestTransfer)
    {
        this.lifeGoals = lifeGoals;
        this.files = files;
        this.cloud = cloud;
        this.owner = owner;
        this.time = time;
        this.requestTransfer = requestTransfer;
    }

    /// <summary>Raised whenever a file comes or goes here, so a page waiting for one looks again. It may come from any thread.</summary>
    public event EventHandler? Changed;

    /// <summary>Counts up with every <see cref="Changed"/>.</summary>
    public int Version => Volatile.Read(ref version);

    /// <summary>Adds a shrunk JPEG of <paramref name="width"/> by <paramref name="height"/> pixels to a life goal; null when the life goal is gone.</summary>
    public LifeGoalPicture? Add(string lifeGoalId, byte[] jpeg, int width, int height)
    {
        if (lifeGoals.AddPicture(lifeGoalId, width, height) is not { } picture)
        {
            return null;
        }

        files.Write(picture.Id, jpeg, pending: true);
        Bump();
        requestTransfer();
        return picture;
    }

    /// <summary>The picture's bytes when this device has them.</summary>
    public byte[]? Read(string id) => files.Read(id);

    /// <summary>
    /// Uploads what waits, downloads what is missing and removes what was deleted long enough ago.
    /// False when the server could not be reached, so it is worth trying again later.
    /// </summary>
    public async Task<bool> TransferAsync(CancellationToken cancellationToken = default)
    {
        await running.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            return await TransferOnceAsync(cancellationToken).ConfigureAwait(false);
        }
        finally
        {
            running.Release();
        }
    }

    private async Task<bool> TransferOnceAsync(CancellationToken cancellationToken)
    {
        if (owner() is not { } who)
        {
            return true;
        }

        var before = files.Ids();
        var live = lifeGoals.AllPictures().Select(picture => picture.Id).ToHashSet(StringComparer.Ordinal);
        var tombstones = lifeGoals.PictureTombstones();
        try
        {
            foreach (var id in files.Pending())
            {
                if (live.Contains(id))
                {
                    if (await AttemptAsync(() => files.Read(id) is { } bytes ? cloud.UploadAsync(who, id, bytes, cancellationToken) : Task.CompletedTask)
                        .ConfigureAwait(false))
                    {
                        files.Uploaded(id);
                    }
                }
                else if (tombstones.ContainsKey(id))
                {
                    // Deleted before it went up: nothing to send.
                    files.Uploaded(id);
                }
            }

            foreach (var id in live.Except(files.Ids()))
            {
                await AttemptAsync(async () =>
                {
                    if (await cloud.DownloadAsync(who, id, cancellationToken).ConfigureAwait(false) is { } bytes)
                    {
                        files.Write(id, bytes, pending: false);
                    }
                }).ConfigureAwait(false);
            }

            var cutoff = time.GetUtcNow() - Grace;
            foreach (var (id, deletedAt) in tombstones)
            {
                if (files.Has(id) && !files.Pending().Contains(id) && SyncRules.InstantOf(deletedAt) is { } at && at < cutoff
                    && await AttemptAsync(() => cloud.RemoveAsync(who, id, cancellationToken)).ConfigureAwait(false))
                {
                    files.Delete(id);
                }
            }

            // A row the purge took long ago leaves nothing to keep.
            var pending = files.Pending();
            foreach (var id in files.Ids().Where(id => !live.Contains(id) && !tombstones.ContainsKey(id) && !pending.Contains(id)).ToList())
            {
                files.Delete(id);
            }

            return true;
        }
        catch (RemoteUnavailableException)
        {
            return false;
        }
        finally
        {
            if (!files.Ids().SetEquals(before))
            {
                Bump();
            }
        }
    }

    // One file's call; a refusal (a file too big, a policy) skips that file and leaves the rest.
    private static async Task<bool> AttemptAsync(Func<Task> call)
    {
        try
        {
            await call().ConfigureAwait(false);
            return true;
        }
        catch (RemoteRejectedException)
        {
            return false;
        }
    }

    private void Bump()
    {
        Interlocked.Increment(ref version);
        Changed?.Invoke(this, EventArgs.Empty);
    }
}
