using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Planning;

/// <summary>The owner's tags by name, created through the outbox when the composer names a new one.</summary>
public sealed class TagList
{
    private const string Table = "tags";
    private const int MaxName = 40;
    private readonly IReplica replica;
    private readonly NewRows rows;
    private readonly Action requestSync;

    public TagList(IReplica replica, NewRows rows, Action requestSync)
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

    public event EventHandler? Changed;

    /// <summary>Tag names, oldest first.</summary>
    public IReadOnlyList<string> Names() => [.. Live().Select(row => (string?)row["name"] ?? string.Empty)];

    /// <summary>The id of the tag with this name (ignoring case), created when there is none.</summary>
    public string? FindOrCreate(string name)
    {
        var trimmed = name.Trim();
        trimmed = trimmed.Length > MaxName ? trimmed[..MaxName] : trimmed;
        if (trimmed.Length == 0)
        {
            return null;
        }

        var wanted = trimmed.ToLowerInvariant();
        if (Live().FirstOrDefault(row => ((string?)row["name"] ?? string.Empty).ToLowerInvariant() == wanted) is { } existing)
        {
            return (string?)existing[SyncedTable.Id];
        }

        var row = rows.Create(Table, new Dictionary<string, JsonNode?> { ["name"] = trimmed });
        if (row is null)
        {
            return null;
        }

        replica.Queue(Table, row);
        requestSync();
        return (string?)row[SyncedTable.Id];
    }

    private IEnumerable<JsonObject> Live() => replica.All(Table)
        .Where(row => row[SyncedTable.DeletedAt] is null)
        .OrderBy(row => (string?)row[SyncedTable.CreatedAt], StringComparer.Ordinal);
}
