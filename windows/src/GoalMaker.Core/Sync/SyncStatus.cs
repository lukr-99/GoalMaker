namespace GoalMaker.Core.Sync;

/// <summary>The device's sync state, the last success, and how many changes are still local.</summary>
public sealed record SyncStatus(SyncState State, DateTimeOffset? LastSyncedAt, int PendingChanges, string? Problem)
{
    public static SyncStatus Initial { get; } = new(SyncState.Idle, null, 0, null);
}
