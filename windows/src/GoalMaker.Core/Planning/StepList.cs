using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Planning;

/// <summary>A task's checklist: read from the replica, written through its outbox.</summary>
public sealed class StepList
{
    private const string Table = "task_steps";
    private const int MaxTitle = 300;
    private readonly IReplica replica;
    private readonly NewRows rows;
    private readonly Action requestSync;

    public StepList(IReplica replica, NewRows rows, Action requestSync)
    {
        this.replica = replica;
        this.rows = rows;
        this.requestSync = requestSync;
        replica.Changed += (_, table) =>
        {
            if (table == Table)
            {
                Changed?.Invoke(this, EventArgs.Empty);
            }
        };
    }

    /// <summary>Raised after every change to the steps table.</summary>
    public event EventHandler? Changed;

    /// <summary>The steps of <paramref name="taskId"/> in their order.</summary>
    public IReadOnlyList<StepItem> ForTask(string taskId) => [.. Live(taskId).Select(ToItem)];

    /// <summary>Adds a step at the end. Null when the title is blank or nobody is signed in.</summary>
    public StepItem? Add(string taskId, string title)
    {
        var trimmed = Trimmed(title);
        if (trimmed.Length == 0)
        {
            return null;
        }

        var existing = Live(taskId);
        var position = existing.Count == 0 ? 0.0 : existing.Max(Position) + 1.0;
        var row = rows.Create(Table, new Dictionary<string, JsonNode?>
        {
            ["task_id"] = taskId,
            ["title"] = trimmed,
            ["done"] = false,
            ["position"] = position,
        });
        if (row is null)
        {
            return null;
        }

        replica.Queue(Table, row);
        requestSync();
        return ToItem(row);
    }

    /// <summary>A new title. False when it is blank.</summary>
    public bool Rename(string id, string title)
    {
        var trimmed = Trimmed(title);
        return trimmed.Length > 0 && Change(id, row => row["title"] = trimmed);
    }

    public bool SetDone(string id, bool done) => Change(id, row => row["done"] = done);

    public bool Delete(string id) => Change(id, row => row[SyncedTable.DeletedAt] = rows.Timestamp());

    /// <summary>Moves a step to <paramref name="index"/> in its checklist and numbers the checklist again.</summary>
    public void Move(string id, int index)
    {
        if (replica.Get(Table, id) is not { } step)
        {
            return;
        }

        var order = Live((string?)step["task_id"] ?? string.Empty).ToList();
        var at = order.FindIndex(row => (string?)row[SyncedTable.Id] == id);
        if (at < 0)
        {
            return;
        }

        var moved = order[at];
        order.RemoveAt(at);
        order.Insert(Math.Clamp(index, 0, order.Count), moved);
        replica.InTransaction(() =>
        {
            for (var position = 0; position < order.Count; position++)
            {
                order[position]["position"] = (double)position;
                replica.Queue(Table, order[position]);
            }
        });
        requestSync();
    }

    private static string Trimmed(string title)
    {
        var trimmed = title.Trim();
        return trimmed.Length > MaxTitle ? trimmed[..MaxTitle] : trimmed;
    }

    private static double Position(JsonObject row) => row["position"] is JsonValue value && value.TryGetValue<double>(out var number) ? number
        : row["position"] is JsonValue whole && whole.TryGetValue<long>(out var integer) ? integer
        : 0;

    private static StepItem ToItem(JsonObject row) => new(
        (string?)row[SyncedTable.Id] ?? string.Empty,
        (string?)row["task_id"] ?? string.Empty,
        (string?)row["title"] ?? string.Empty,
        row["done"] is JsonValue done && done.TryGetValue<bool>(out var flag) && flag);

    private List<JsonObject> Live(string taskId) => [.. replica.All(Table)
        .Where(row => (string?)row["task_id"] == taskId && row[SyncedTable.DeletedAt] is null)
        .OrderBy(Position)
        .ThenBy(row => (string?)row[SyncedTable.CreatedAt], StringComparer.Ordinal)
        .ThenBy(row => (string?)row[SyncedTable.Id], StringComparer.Ordinal)];

    private bool Change(string id, Action<JsonObject> edit)
    {
        if (replica.Get(Table, id) is not { } row || row[SyncedTable.DeletedAt] is not null)
        {
            return false;
        }

        edit(row);
        replica.Queue(Table, row);
        requestSync();
        return true;
    }
}
