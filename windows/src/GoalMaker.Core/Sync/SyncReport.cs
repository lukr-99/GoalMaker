namespace GoalMaker.Core.Sync;

/// <summary>What one sync run did.</summary>
public sealed record SyncReport(int Pushed, int Rejected, int Pulled, bool Offline, string? Problem);
