using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Tests.Sync;

/// <summary>
/// An in-memory stand-in for PostgREST that behaves like the real tables: the server stamps
/// updated_at (a microsecond clock that only moves forward), keeps owner_id and created_at, returns
/// rows in (updated_at, id) order and pages by the same cursor rule.
/// </summary>
internal sealed class FakeServer : IRemoteTables
{
    private readonly Dictionary<string, Dictionary<string, JsonObject>> tables = [];
    private DateTimeOffset clock = new(2026, 9, 18, 10, 0, 0, TimeSpan.Zero);

    public bool Offline { get; set; }

    public HashSet<string> RefusedIds { get; } = [];

    public int Upserts { get; private set; }

    public IReadOnlyCollection<JsonObject> Rows(string table) =>
        tables.TryGetValue(table, out var rows) ? rows.Values : [];

    /// <summary>Stores a row as if another device had pushed it.</summary>
    public JsonObject Seed(string table, JsonObject row) => Store(table, row);

    public void Advance(TimeSpan by) => clock += by;

    public Task<JsonObject> UpsertAsync(string table, JsonObject row, CancellationToken cancellationToken)
    {
        if (Offline)
        {
            throw new RemoteUnavailableException("offline");
        }

        if (RefusedIds.Contains((string)row["id"]!))
        {
            throw new RemoteRejectedException("HTTP 403: row security");
        }

        Assert.False(row.ContainsKey("updated_at"), "the server owns updated_at; clients must not send it");
        Upserts++;
        return Task.FromResult((JsonObject)Store(table, row).DeepClone());
    }

    public Task<IReadOnlyList<JsonObject>> PullAsync(string table, string? from, RowCursor? after, int limit, CancellationToken cancellationToken)
    {
        if (Offline)
        {
            throw new RemoteUnavailableException("offline");
        }

        IReadOnlyList<JsonObject> page = [.. Rows(table)
            .Where(row => from is null || string.CompareOrdinal((string)row["updated_at"]!, from) >= 0)
            .Where(row => after is null
                || string.CompareOrdinal((string)row["updated_at"]!, after.UpdatedAt) > 0
                || ((string)row["updated_at"]! == after.UpdatedAt && string.CompareOrdinal((string)row["id"]!, after.Id) > 0))
            .OrderBy(row => (string)row["updated_at"]!, StringComparer.Ordinal)
            .ThenBy(row => (string)row["id"]!, StringComparer.Ordinal)
            .Take(limit)
            .Select(row => (JsonObject)row.DeepClone())];
        return Task.FromResult(page);
    }

    private JsonObject Store(string table, JsonObject row)
    {
        if (!tables.TryGetValue(table, out var rows))
        {
            rows = tables[table] = [];
        }

        var id = (string)row["id"]!;
        var stored = (JsonObject)row.DeepClone();
        if (rows.TryGetValue(id, out var existing))
        {
            stored["owner_id"] = existing["owner_id"]?.DeepClone();
            stored["created_at"] = existing["created_at"]?.DeepClone();
        }

        clock = clock.AddTicks(10);
        // PostgREST's text form, which the engine must normalize.
        stored["updated_at"] = clock.ToString("yyyy-MM-dd'T'HH:mm:ss.ffffff'+00:00'", System.Globalization.CultureInfo.InvariantCulture);
        rows[id] = stored;
        return stored;
    }
}
