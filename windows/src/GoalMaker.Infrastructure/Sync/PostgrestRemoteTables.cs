using System.Text;
using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;
using GoalMaker.Infrastructure.Postgrest;

namespace GoalMaker.Infrastructure.Sync;

/// <summary>
/// <see cref="IRemoteTables"/> over PostgREST with the publishable key and the user's access token.
/// Generic JSON rows, so the synced-table contract drives everything.
/// </summary>
public sealed class PostgrestRemoteTables(PostgrestHttp postgrest) : IRemoteTables
{
    public async Task<JsonObject> UpsertAsync(string table, JsonObject row, CancellationToken cancellationToken)
    {
        var body = (JsonObject)row.DeepClone();
        using var request = postgrest.Request(HttpMethod.Post, $"{table}?on_conflict=id");
        request.Headers.Add("Prefer", "resolution=merge-duplicates,return=representation");
        request.Content = new StringContent(new JsonArray(body).ToJsonString(), Encoding.UTF8, "application/json");
        var rows = Rows(await postgrest.SendAsync(request, cancellationToken).ConfigureAwait(false));
        return rows.Count == 1 && rows[0] is JsonObject stored
            ? stored
            : throw new RemoteRejectedException($"The server stored no {table} row for {row[SyncedTable.Id]}.");
    }

    public async Task<IReadOnlyList<JsonObject>> PullAsync(
        string table,
        string? from,
        RowCursor? after,
        int limit,
        CancellationToken cancellationToken)
    {
        var query = new StringBuilder($"{table}?select=*&order=updated_at.asc,id.asc&limit={limit}");
        if (from is not null)
        {
            query.Append("&updated_at=gte.").Append(Uri.EscapeDataString(from));
        }

        if (after is not null)
        {
            var at = Uri.EscapeDataString(after.UpdatedAt);
            var id = Uri.EscapeDataString(after.Id);
            query.Append($"&or=(updated_at.gt.{at},and(updated_at.eq.{at},id.gt.{id}))");
        }

        using var request = postgrest.Request(HttpMethod.Get, query.ToString());
        var rows = Rows(await postgrest.SendAsync(request, cancellationToken).ConfigureAwait(false));
        return [.. rows.OfType<JsonObject>()];
    }

    private static JsonArray Rows(string text) => JsonNode.Parse(text) as JsonArray ?? [];
}
