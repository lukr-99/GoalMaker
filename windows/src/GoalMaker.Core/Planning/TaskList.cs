using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Planning;

/// <summary>
/// Tasks as the M1 lists need them: read from the replica, written through its outbox. Every write
/// asks for a sync. M2 grows this into the planning use cases (days, Plan tomorrow, repeats).
/// </summary>
public sealed class TaskList
{
    private const string Table = "tasks";
    private readonly SyncedTable table;
    private readonly IReplica replica;
    private readonly Func<string?> ownerId;
    private readonly TimeProvider time;
    private readonly Action requestSync;

    public TaskList(SyncedTableCatalog catalog, IReplica replica, Func<string?> ownerId, TimeProvider time, Action requestSync)
    {
        table = catalog[Table];
        this.replica = replica;
        this.ownerId = ownerId;
        this.time = time;
        this.requestSync = requestSync;
        replica.Changed += (_, changed) =>
        {
            if (changed == Table)
            {
                Changed?.Invoke(this, EventArgs.Empty);
            }
        };
    }

    public event EventHandler? Changed;

    /// <summary>Open, not deleted, oldest first.</summary>
    public IReadOnlyList<TaskItem> Open() =>
        [.. replica.All(Table)
            .Where(row => row[SyncedTable.DeletedAt] is null && (string?)row["status"] == "open")
            .Select(ToItem)
            .OrderBy(item => item.CreatedAt, StringComparer.Ordinal)];

    public TaskItem? Add(string title)
    {
        var trimmed = title.Trim();
        var owner = ownerId();
        if (trimmed.Length == 0 || owner is null)
        {
            return null;
        }

        var row = new JsonObject();
        foreach (var column in table.Columns)
        {
            row[column.Name] = null;
        }

        row[SyncedTable.Id] = Guid.NewGuid().ToString();
        row[SyncedTable.OwnerId] = owner;
        row["title"] = trimmed.Length > 500 ? trimmed[..500] : trimmed;
        row["notes"] = string.Empty;
        row["top_priority"] = false;
        row["status"] = "open";
        row["position"] = 0.0;
        row[SyncedTable.CreatedAt] = Now();
        row[SyncedTable.UpdatedAt] = string.Empty;
        replica.Queue(Table, row);
        requestSync();
        return ToItem(row);
    }

    public void SetDone(string id, bool done) => Change(id, row =>
    {
        row["status"] = done ? "done" : "open";
        row["completed_at"] = done ? Now() : null;
    });

    public void Delete(string id) => Change(id, row => row[SyncedTable.DeletedAt] = Now());

    private void Change(string id, Action<JsonObject> edit)
    {
        var row = replica.Get(Table, id);
        if (row is null)
        {
            return;
        }

        edit(row);
        replica.Queue(Table, row);
        requestSync();
    }

    private string Now() => SyncRules.Format(time.GetUtcNow());

    private static TaskItem ToItem(JsonObject row) => new(
        (string)row[SyncedTable.Id]!,
        (string?)row["title"] ?? string.Empty,
        (string?)row["status"] switch
        {
            "done" => TaskState.Done,
            "dropped" => TaskState.Dropped,
            _ => TaskState.Open,
        },
        (bool?)row["top_priority"] ?? false,
        (string?)row[SyncedTable.CreatedAt] ?? string.Empty);
}
