namespace GoalMaker.Core.Sync;

/// <summary>How a synced column is stored locally and sent on the wire (contracts/schemas/synced-tables.json).</summary>
public enum ColumnKind
{
    Text,
    Integer,
    Real,
    Boolean,
    Timestamp,
    Date,
    Time,

    /// <summary>A JSON value: its JSON text in the replica, the value itself on the wire.</summary>
    Json,
}
