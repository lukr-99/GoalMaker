namespace GoalMaker.Core.Sync;

/// <summary>Where the next page of a pull starts: after this (updated_at, id).</summary>
public sealed record RowCursor(string UpdatedAt, string Id);
