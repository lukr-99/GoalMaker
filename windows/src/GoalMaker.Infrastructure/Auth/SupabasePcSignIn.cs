using System.Net;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using GoalMaker.Core.Account;
using GoalMaker.Core.Auth;

namespace GoalMaker.Infrastructure.Auth;

/// <summary>
/// <see cref="IPcSignIn"/> over the <c>pc-sign-in</c> Edge Function (ADR 0020). The PC is not signed
/// in, so the call carries only the publishable key; the function checks the request's own secrets.
/// </summary>
public sealed class SupabasePcSignIn(HttpClient http, string baseUrl, string publishableKey) : IPcSignIn
{
    private readonly string functionUrl = baseUrl.TrimEnd('/') + "/functions/v1/pc-sign-in";

    public async Task<PcSignInStart> StartAsync(string device, CancellationToken cancellationToken)
    {
        var (status, body) = await SendAsync(new JsonObject { ["action"] = "start", ["device"] = device }, cancellationToken)
            .ConfigureAwait(false);
        return status switch
        {
            null => new PcSignInStart.Offline(),
            HttpStatusCode.OK when Text(body, "id") is { } id && Text(body, "scan") is { } scan
                && Text(body, "pickup") is { } pickup && (body as JsonObject)?["expires_in"] is JsonValue seconds
                && seconds.TryGetValue<int>(out var lifetime) && PcSignInCode.ScanSecretOf(PcSignInCode.QrText(scan)) is not null
                && (body as JsonObject)?["match"] is JsonValue matchValue && matchValue.TryGetValue<int>(out var match)
                && PcSignInCode.IsMatchNumber(match) =>
                new PcSignInStart.Started(id, scan, pickup, match, TimeSpan.FromSeconds(lifetime)),
            // No function by that name: this server has no QR sign-in yet.
            HttpStatusCode.NotFound => new PcSignInStart.Unavailable(),
            // Too many open codes, or too many calls from this network.
            HttpStatusCode.TooManyRequests => new PcSignInStart.Busy(),
            _ => new PcSignInStart.Failed(),
        };
    }

    public async Task<PcSignInState> PickUpAsync(PcSignInStart.Started request, CancellationToken cancellationToken)
    {
        var call = new JsonObject { ["action"] = "pick_up", ["id"] = request.Id, ["pickup"] = request.PickupSecret };
        var (status, body) = await SendAsync(call, cancellationToken).ConfigureAwait(false);
        if (status is null)
        {
            return new PcSignInState.Offline();
        }

        if (status != HttpStatusCode.OK)
        {
            return new PcSignInState.Failed();
        }

        return Text(body, "state") switch
        {
            "waiting" => new PcSignInState.Waiting(),
            "declined" => new PcSignInState.Declined(),
            "expired" => new PcSignInState.Expired(),
            "approved" when EmailAddress.Parse(Text(body, "email") ?? string.Empty) is { } email
                && SignInCode.Parse(Text(body, "code") ?? string.Empty) is { } code => new PcSignInState.Approved(email, code),
            _ => new PcSignInState.Failed(),
        };
    }

    /// <summary>The status and the JSON body, or a null status when the server wasn't reached.</summary>
    private async Task<(HttpStatusCode? Status, JsonNode? Body)> SendAsync(JsonObject call, CancellationToken cancellationToken)
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, functionUrl)
        {
            Content = new StringContent(call.ToJsonString(), Encoding.UTF8, "application/json"),
        };
        request.Headers.Add("apikey", publishableKey);
        try
        {
            using var response = await http.SendAsync(request, cancellationToken).ConfigureAwait(false);
            var text = await response.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false);
            return (response.StatusCode, Parse(text));
        }
        catch (HttpRequestException)
        {
            return (null, null);
        }
        catch (TaskCanceledException) when (!cancellationToken.IsCancellationRequested)
        {
            return (null, null);
        }
    }

    private static JsonNode? Parse(string text)
    {
        try
        {
            return JsonNode.Parse(text);
        }
        catch (JsonException)
        {
            return null;
        }
    }

    private static string? Text(JsonNode? body, string name) =>
        (body as JsonObject)?[name] is JsonValue value && value.TryGetValue<string>(out var text) ? text : null;
}
