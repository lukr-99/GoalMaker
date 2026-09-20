using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Planning;

/// <summary>
/// Complete rows for a local insert: every described column, a new id, the signed-in owner, the
/// creation time, and an empty updated_at for the server to stamp (docs/sync.md).
/// </summary>
public sealed class NewRows(SyncedTableCatalog catalog, Func<string?> ownerId, TimeProvider time, Func<string>? newId = null)
{
    private readonly Func<string> newId = newId ?? (() => Guid.NewGuid().ToString());

    /// <summary>The owner rows are written for, or null when nobody is signed in.</summary>
    public string? Owner() => ownerId();

    public string Timestamp() => SyncRules.Format(time.GetUtcNow());

    /// <summary>
    /// A new row of <paramref name="table"/> with <paramref name="values"/> set, or null when nobody
    /// is signed in. A column the server needs a value in must have one: a null there would be
    /// refused on the push, so it fails here at the write, where the test that covers the write can
    /// see it.
    /// </summary>
    public JsonObject? Create(string table, IReadOnlyDictionary<string, JsonNode?> values)
    {
        if (ownerId() is not { } owner)
        {
            return null;
        }

        var described = catalog[table];
        var row = new JsonObject();
        foreach (var column in described.Columns)
        {
            row[column.Name] = null;
        }

        row[SyncedTable.Id] = newId();
        row[SyncedTable.OwnerId] = owner;
        row[SyncedTable.CreatedAt] = Timestamp();
        row[SyncedTable.UpdatedAt] = string.Empty;
        foreach (var (name, value) in values)
        {
            row[name] = value?.DeepClone();
        }

        var missing = described.Required.Where(column => row[column] is null).ToList();
        if (missing.Count > 0)
        {
            throw new InvalidOperationException($"a new {table} row needs a value in {string.Join(", ", missing)}");
        }

        return row;
    }
}
