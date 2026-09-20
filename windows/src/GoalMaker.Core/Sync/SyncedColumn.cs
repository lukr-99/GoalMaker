namespace GoalMaker.Core.Sync;

/// <summary>
/// One column of a synced table. <paramref name="Required"/> means the server has it NOT NULL: a row
/// carries every column, so a null in one of these is refused on the push whatever default the
/// column has (contracts/schemas/synced-tables.json).
/// </summary>
public sealed record SyncedColumn(string Name, ColumnKind Kind, bool Required = false);
