namespace GoalMaker.Core.Sync;

/// <summary>One column of a synced table.</summary>
public sealed record SyncedColumn(string Name, ColumnKind Kind);
