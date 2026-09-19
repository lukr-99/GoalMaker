namespace GoalMaker.Core.Activity;

/// <summary>
/// The server's activity log (docs/activity.md). It isn't part of the replica, so it is read online;
/// both calls throw RemoteUnavailableException when the server can't be reached.
/// </summary>
public interface IActivityLog
{
    /// <summary>The latest changes, newest first.</summary>
    Task<IReadOnlyList<ActivityEntry>> RecentAsync(int limit = 60, CancellationToken cancellationToken = default);

    /// <summary>Puts the row back the way the entry found it; the result reaches the replica through sync.</summary>
    Task<UndoOutcome> UndoAsync(long entryId, CancellationToken cancellationToken = default);
}
