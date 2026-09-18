using System.Text.Json.Nodes;

namespace GoalMaker.Core.Sync;

/// <summary>
/// The device's copy of the synced tables, its outbox and watermarks (docs/sync.md). Rows are JSON
/// objects in wire shape: booleans are JSON booleans, timestamps normalized UTC text.
/// </summary>
public interface IReplica
{
    /// <summary>Raised after a commit that changed rows of a table (the table name is the argument).</summary>
    event EventHandler<string>? Changed;

    JsonObject? Get(string table, string id);

    IReadOnlyList<JsonObject> All(string table);

    /// <summary>A local write: stores the row and queues it for the push, in one transaction.</summary>
    void Queue(string table, JsonObject row);

    bool IsPending(string table, string id);

    int PendingCount();

    IReadOnlyList<OutboxEntry> Outbox();

    /// <summary>Stores the server's copy and removes the entry, unless the row changed again meanwhile.</summary>
    void CompletePush(OutboxEntry entry, JsonObject serverRow);

    void FailPush(OutboxEntry entry, string error);

    /// <summary>Stores a row from the server as it is.</summary>
    void Put(string table, JsonObject row);

    void DropPending(string table, string id);

    string? Watermark(string table);

    void SetWatermark(string table, string watermark);

    /// <summary>Removes the table's rows that have no pending change, and its watermark (full resync).</summary>
    void ClearSynced(string table);

    /// <summary>Empties every synced table, the outbox and the watermarks (sign-out).</summary>
    void ClearAll();

    void InTransaction(Action work);
}
