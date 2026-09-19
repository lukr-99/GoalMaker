using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Planning;

/// <summary>The owner's tags by name, created through the outbox when the composer names a new one.</summary>
public sealed class TagList
{
    private const string Table = "tags";
    private const string Links = "task_tags";
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

    /// <summary>Every tag that isn't deleted, oldest first.</summary>
    public IReadOnlyList<TagItem> All() => [.. Live().Select(row => new TagItem((string?)row[SyncedTable.Id] ?? string.Empty, (string?)row["name"] ?? string.Empty))];

    /// <summary>Tag names, oldest first.</summary>
    public IReadOnlyList<string> Names() => [.. All().Select(tag => tag.Name)];

    /// <summary>Renames a tag. False when the name is blank or another tag already has it (ignoring case).</summary>
    public bool Rename(string id, string name)
    {
        var trimmed = Trimmed(name);
        if (trimmed.Length == 0 || (Find(trimmed) is { } clash && clash.Id != id) || replica.Get(Table, id) is not { } row || row[SyncedTable.DeletedAt] is not null)
        {
            return false;
        }

        row["name"] = trimmed;
        replica.Queue(Table, row);
        requestSync();
        return true;
    }

    /// <summary>Deletes a tag and its links to tasks; the tasks stay.</summary>
    public void Delete(string id)
    {
        var stamp = rows.Timestamp();
        replica.InTransaction(() =>
        {
            if (replica.Get(Table, id) is not { } row || row[SyncedTable.DeletedAt] is not null)
            {
                return;
            }

            row[SyncedTable.DeletedAt] = stamp;
            replica.Queue(Table, row);
            foreach (var link in replica.All(Links).Where(link => link[SyncedTable.DeletedAt] is null && (string?)link["tag_id"] == id))
            {
                link[SyncedTable.DeletedAt] = stamp;
                replica.Queue(Links, link);
            }
        });
        requestSync();
    }

    /// <summary>Each task's tags: task id to the ids of the live tags linked to it, for the list filter.</summary>
    public IReadOnlyDictionary<string, IReadOnlySet<string>> TagLinks()
    {
        var live = All().Select(tag => tag.Id).ToHashSet(StringComparer.Ordinal);
        return replica.All(Links)
            .Where(link => link[SyncedTable.DeletedAt] is null && (string?)link["tag_id"] is { } tag && live.Contains(tag))
            .GroupBy(link => (string?)link["task_id"] ?? string.Empty, link => (string)link["tag_id"]!, StringComparer.Ordinal)
            .ToDictionary(group => group.Key, group => (IReadOnlySet<string>)group.ToHashSet(StringComparer.Ordinal), StringComparer.Ordinal);
    }

    /// <summary>The id of the tag with this name (ignoring case), created when there is none.</summary>
    public string? FindOrCreate(string name)
    {
        var trimmed = Trimmed(name);
        if (trimmed.Length == 0)
        {
            return null;
        }

        if (Find(trimmed) is { } existing)
        {
            return existing.Id;
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

    /// <summary>The tags linked to one task, oldest tag first.</summary>
    public IReadOnlyList<TagItem> ForTask(string taskId)
    {
        var linked = TagLinks().TryGetValue(taskId, out var ids) ? ids : new HashSet<string>();
        return [.. All().Where(tag => linked.Contains(tag.Id))];
    }

    private static string Trimmed(string name)
    {
        var trimmed = name.Trim();
        return trimmed.Length > MaxName ? trimmed[..MaxName] : trimmed;
    }

    private TagItem? Find(string name)
    {
        var wanted = name.Trim().ToLowerInvariant();
        return All().FirstOrDefault(tag => tag.Name.ToLowerInvariant() == wanted);
    }

    private IEnumerable<JsonObject> Live() => replica.All(Table)
        .Where(row => row[SyncedTable.DeletedAt] is null)
        .OrderBy(row => (string?)row[SyncedTable.CreatedAt], StringComparer.Ordinal);
}
