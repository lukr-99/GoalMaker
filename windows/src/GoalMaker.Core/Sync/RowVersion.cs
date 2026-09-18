namespace GoalMaker.Core.Sync;

/// <summary>What merging needs to know about one copy of a row: its server timestamp and whether it's a tombstone.</summary>
public sealed record RowVersion(string UpdatedAt, bool Deleted);
