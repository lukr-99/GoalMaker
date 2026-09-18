namespace GoalMaker.Core.Sync;

/// <summary>A local change waiting to be pushed: the full row as JSON, in queue order.</summary>
public sealed record OutboxEntry(long Seq, string Entity, string RowId, string Payload, int Attempts, string? LastError);
