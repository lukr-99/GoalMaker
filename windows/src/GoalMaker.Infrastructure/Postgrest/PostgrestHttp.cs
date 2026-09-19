using System.Net;
using System.Net.Http.Headers;
using System.Text.Json;
using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;

namespace GoalMaker.Infrastructure.Postgrest;

/// <summary>
/// Calls to PostgREST (<c>/rest/v1/...</c>) as the signed-in user: the publishable key and the user's
/// access token. A failure is <see cref="RemoteUnavailableException"/> when trying again later can help
/// (offline, a timeout, an expired token, the server busy) and <see cref="RemoteRejectedException"/>,
/// with the database's SQLSTATE when there is one, when it can't.
/// </summary>
public sealed class PostgrestHttp(HttpClient http, string baseUrl, string publishableKey, Func<string?> accessToken)
{
    private readonly string restUrl = baseUrl.TrimEnd('/') + "/rest/v1/";

    /// <summary>A request to <paramref name="path"/> (a table with its query, or <c>rpc/&lt;function&gt;</c>).</summary>
    public HttpRequestMessage Request(HttpMethod method, string path)
    {
        var token = accessToken() ?? throw new RemoteUnavailableException("Not signed in.");
        var request = new HttpRequestMessage(method, restUrl + path);
        request.Headers.Add("apikey", publishableKey);
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
        return request;
    }

    /// <summary>Sends <paramref name="request"/> and returns the body of a success.</summary>
    public async Task<string> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
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
                return text;
            }

            var status = (int)response.StatusCode;
            var temporary = response.StatusCode is HttpStatusCode.Unauthorized or HttpStatusCode.RequestTimeout
                or HttpStatusCode.TooManyRequests || status >= 500;
            var message = $"HTTP {status}: {(text.Length <= 300 ? text : text[..300])}";
            throw temporary ? new RemoteUnavailableException(message) : new RemoteRejectedException(message, SqlState(text));
        }
    }

    // PostgREST reports a database error as {"code": "40001", "message": ...}.
    private static string? SqlState(string body)
    {
        try
        {
            return JsonNode.Parse(body) is JsonObject error && error["code"] is JsonValue code ? code.ToString() : null;
        }
        catch (JsonException)
        {
            return null;
        }
    }
}
