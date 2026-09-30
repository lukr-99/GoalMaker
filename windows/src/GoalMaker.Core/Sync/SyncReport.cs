namespace GoalMaker.Core.Sync;

/// <summary>
/// What one sync run did. <paramref name="Unauthorized"/> is an offline run the server refused the
/// session for, which renewing the session may fix.
/// </summary>
public sealed record SyncReport(int Pushed, int Rejected, int Pulled, bool Offline, string? Problem, bool Unauthorized = false);
