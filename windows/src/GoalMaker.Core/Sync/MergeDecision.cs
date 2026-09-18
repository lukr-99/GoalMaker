namespace GoalMaker.Core.Sync;

/// <summary>The outcome of merging one pulled row (contracts/vectors/sync-merge.json).</summary>
public sealed record MergeDecision(bool TakeRemote, bool DropPending);
