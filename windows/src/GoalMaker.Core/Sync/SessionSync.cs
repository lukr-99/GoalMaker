using GoalMaker.Core.Auth;

namespace GoalMaker.Core.Sync;

/// <summary>
/// Keeps sync in step with the session (docs/sync.md, docs/sign-in.md). A sign-in syncs, after
/// emptying a replica that holds another owner's rows, so the last owner's outbox never reaches the
/// new account. Any sign-out stops the scheduled runs and leaves the replica alone: only the sign-out
/// asked for in Settings clears it (<see cref="SyncCoordinator.FlushAndClearAsync"/>), so a session
/// the week or the server ended keeps its outbox for the same owner.
/// </summary>
public sealed class SessionSync(SyncedTableCatalog catalog, IReplica replica, SyncCoordinator sync)
{
    public void Apply(AuthSession session)
    {
        if (session is not AuthSession.SignedIn signedIn)
        {
            sync.CancelScheduled();
            return;
        }

        ForgetOtherOwners(signedIn.UserId);
        sync.Request();
    }

    // A replica only ever holds one owner's rows; signing in as someone else starts clean.
    private void ForgetOtherOwners(string ownerId)
    {
        var foreign = catalog.Tables.Any(table => replica.All(table.Name)
            .Any(row => (string?)row[SyncedTable.OwnerId] is { } owner && owner != ownerId));
        if (foreign)
        {
            replica.ClearAll();
        }
    }
}
