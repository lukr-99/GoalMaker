using System.Globalization;
using System.Text;
using System.Text.Json.Nodes;
using GoalMaker.Core.Connector;
using GoalMaker.Core.Sync;
using GoalMaker.Infrastructure.Postgrest;

namespace GoalMaker.Infrastructure.Connector;

/// <summary><see cref="IConnectorLinks"/> over PostgREST: <c>connector_links</c> and the functions that make and revoke links (0007).</summary>
public sealed class PostgrestConnectorLinks(PostgrestHttp postgrest) : IConnectorLinks
{
    public async Task<IReadOnlyList<ConnectorLink>> ListAsync(CancellationToken cancellationToken = default)
    {
        using var request = postgrest.Request(HttpMethod.Get, "connector_links?select=id,created_at,last_used_at,revoked_at&order=created_at.desc");
        var text = await postgrest.SendAsync(request, cancellationToken).ConfigureAwait(false);
        return [.. (JsonNode.Parse(text) as JsonArray ?? []).OfType<JsonObject>().Select(row => new ConnectorLink(
            (string)row["id"]!,
            Instant((string?)row["created_at"]) ?? DateTimeOffset.UnixEpoch,
            Instant((string?)row["last_used_at"]),
            Instant((string?)row["revoked_at"])))];
    }

    public async Task<string> CreateAsync(CancellationToken cancellationToken = default)
    {
        var text = await CallAsync("rpc/create_connector_link", cancellationToken).ConfigureAwait(false);
        // A function returning text answers with a JSON string.
        return JsonNode.Parse(text) is JsonValue secret && secret.TryGetValue<string>(out var value)
            ? value
            : throw new RemoteRejectedException("The server returned no connector secret.");
    }

    public async Task RevokeAsync(CancellationToken cancellationToken = default) =>
        await CallAsync("rpc/revoke_connector_links", cancellationToken).ConfigureAwait(false);

    private async Task<string> CallAsync(string function, CancellationToken cancellationToken)
    {
        using var request = postgrest.Request(HttpMethod.Post, function);
        request.Content = new StringContent("{}", Encoding.UTF8, "application/json");
        return await postgrest.SendAsync(request, cancellationToken).ConfigureAwait(false);
    }

    private static DateTimeOffset? Instant(string? text) =>
        text is not null && DateTimeOffset.TryParse(text, CultureInfo.InvariantCulture, DateTimeStyles.AssumeUniversal, out var instant)
            ? instant
            : null;
}
