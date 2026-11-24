using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>A replica that runs a change right after a read of the tasks has taken its rows.</summary>
internal sealed class RacingReplica(IReplica inner) : IReplica
{
    public event EventHandler<string>? Changed
    {
        add => inner.Changed += value;
        remove => inner.Changed -= value;
    }

    public Action? DuringNextRead { get; set; }

    public IReadOnlyList<JsonObject> All(string table)
    {
        var rows = inner.All(table);
        if (table == "tasks" && DuringNextRead is { } change)
        {
            DuringNextRead = null;
            change();
        }

        return rows;
    }

    public JsonObject? Get(string table, string id) => inner.Get(table, id);

    public void Queue(string table, JsonObject row) => inner.Queue(table, row);

    public bool IsPending(string table, string id) => inner.IsPending(table, id);

    public int PendingCount() => inner.PendingCount();

    public IReadOnlyList<OutboxEntry> Outbox() => inner.Outbox();

    public void CompletePush(OutboxEntry entry, JsonObject serverRow) => inner.CompletePush(entry, serverRow);

    public void FailPush(OutboxEntry entry, string error) => inner.FailPush(entry, error);

    public void Put(string table, JsonObject row) => inner.Put(table, row);

    public void DropPending(string table, string id) => inner.DropPending(table, id);

    public string? Watermark(string table) => inner.Watermark(table);

    public void SetWatermark(string table, string watermark) => inner.SetWatermark(table, watermark);

    public void ClearSynced(string table) => inner.ClearSynced(table);

    public void ClearAll() => inner.ClearAll();

    public void InTransaction(Action work) => inner.InTransaction(work);
}
