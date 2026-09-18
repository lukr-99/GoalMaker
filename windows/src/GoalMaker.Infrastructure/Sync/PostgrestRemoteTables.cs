using System.Net;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;

namespace GoalMaker.Infrastructure.Sync;

/// <summary>
/// <see cref="IRemoteTables"/> over PostgREST with the publishable key and the user's access token.
/// Generic JSON rows, so the synced-table contract drives everything.
/// </summary>
public sealed class PostgrestRemoteTables(HttpClient http, string baseUrl, string publishableKey, Func<string?> accessToken)
    : IRemoteTables
{
    private readonly string restUrl = baseUrl.TrimEnd('/') + "/rest/v1/";

    public async Task<JsonObject> UpsertAsync(string table, JsonObject row, CancellationToken cancellationToken)
    {
        var body = (JsonObject)row.DeepClone();
        using var request = Request(HttpMethod.Post, $"{table}?on_conflict=id");
        request.Headers.Add("Prefer", "resolution=merge-duplicates,return=representation");
        request.Content = new StringContent(new JsonArray(body).ToJsonString(), Encoding.UTF8, "application/json");
        var rows = await SendAsync(request, cancellationToken).ConfigureAwait(false);
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

        using var request = Request(HttpMethod.Get, query.ToString());
        var rows = await SendAsync(request, cancellationToken).ConfigureAwait(false);
        return [.. rows.OfType<JsonObject>()];
    }

    private HttpRequestMessage Request(HttpMethod method, string path)
    {
        var token = accessToken() ?? throw new RemoteUnavailableException("Not signed in.");
        var request = new HttpRequestMessage(method, restUrl + path);
        request.Headers.Add("apikey", publishableKey);
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
        return request;
    }

    private async Task<JsonArray> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        HttpResponseMessage response;
        try
        {
            response = await http.SendAsync(request, cancellationToken).ConfigureAwait(false);
        }
        catch (HttpRequestException error)
        {
            throw new RemoteUnavailableException("The server can't be reached.", error);
        }
        catch (TaskCanceledException error) when (!cancellationToken.IsCancellationRequested)
        {
            throw new RemoteUnavailableException("The server didn't answer in time.", error);
        }

        using (response)
        {
            var text = await response.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false);
            if (response.IsSuccessStatusCode)
            {
                return JsonNode.Parse(text) as JsonArray ?? [];
            }

            var status = (int)response.StatusCode;
            var temporary = response.StatusCode is HttpStatusCode.Unauthorized or HttpStatusCode.RequestTimeout
                or HttpStatusCode.TooManyRequests || status >= 500;
            var message = $"HTTP {status}: {Trim(text)}";
            throw temporary ? new RemoteUnavailableException(message) : new RemoteRejectedException(message);
        }
    }

    private static string Trim(string text) => text.Length <= 300 ? text : text[..300];
}
