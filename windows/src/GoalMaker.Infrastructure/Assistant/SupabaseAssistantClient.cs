using System.Net;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using GoalMaker.Core.Assistant;

namespace GoalMaker.Infrastructure.Assistant;

/// <summary>
/// <see cref="IAssistantClient"/> over the <c>assistant</c> Edge Function (the M7 plan, "The call"):
/// <c>POST /functions/v1/assistant</c> with the owner's access token and the whole thread, answered by
/// <c>{"text": ...}</c> or <c>{"error": code, "message": ...}</c>. A refused session is
/// <see cref="AssistantProblem.SignedOut"/>, and <paramref name="unauthorized"/> hears of it so the
/// session gets renewed or ended, as for PostgREST calls (docs/sign-in.md).
/// </summary>
public sealed class SupabaseAssistantClient(
    HttpClient http, string baseUrl, string publishableKey, Func<string?> accessToken, Action? unauthorized = null) : IAssistantClient
{
    private readonly string functionUrl = baseUrl.TrimEnd('/') + "/functions/v1/assistant";

    public async Task<AssistantReply> SendAsync(IReadOnlyList<AssistantMessage> messages, CancellationToken cancellationToken = default)
    {
        if (accessToken() is not { Length: > 0 } token)
        {
            return new AssistantReply.Failure(AssistantProblem.SignedOut);
        }

        using var request = new HttpRequestMessage(HttpMethod.Post, functionUrl)
        {
            Content = new StringContent(Body(messages), Encoding.UTF8, "application/json"),
        };
        request.Headers.Add("apikey", publishableKey);
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));

        HttpResponseMessage response;
        try
        {
            response = await http.SendAsync(request, cancellationToken).ConfigureAwait(false);
        }
        catch (HttpRequestException error)
        {
            return new AssistantReply.Failure(AssistantProblem.Offline, error.Message);
        }
        catch (TaskCanceledException error) when (!cancellationToken.IsCancellationRequested)
        {
            // The server was reached but took too long, which a second try may fix.
            return new AssistantReply.Failure(AssistantProblem.Failed, "The assistant didn't answer in time. " + error.Message);
        }

        using (response)
        {
            var text = await response.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false);
            if (response.StatusCode == HttpStatusCode.Unauthorized)
            {
                unauthorized?.Invoke();
                return new AssistantReply.Failure(AssistantProblem.SignedOut, text);
            }

            var body = Parse(text);
            if (response.IsSuccessStatusCode)
            {
                return body?["text"] is JsonValue answer && answer.TryGetValue<string>(out var said)
                    ? new AssistantReply.Answer(said)
                    : new AssistantReply.Failure(AssistantProblem.Failed, "The answer had no text.");
            }

            var code = body?["error"] is JsonValue error && error.TryGetValue<string>(out var named) ? named : null;
            var detail = body?["message"] is JsonValue message && message.TryGetValue<string>(out var words) ? words : text;
            return new AssistantReply.Failure(Problem(code, response.StatusCode), detail);
        }
    }

    /// <summary>The function's code, or, from something in front of it that has none, the status.</summary>
    private static AssistantProblem Problem(string? code, HttpStatusCode status) => code switch
    {
        "unavailable" => AssistantProblem.Unavailable,
        "rate_limited" => AssistantProblem.RateLimited,
        "provider_limit" => AssistantProblem.ProviderLimit,
        "bad_request" => AssistantProblem.BadRequest,
        "failed" => AssistantProblem.Failed,
        _ => status switch
        {
            // No function by that name: this server has no chat.
            HttpStatusCode.NotFound or HttpStatusCode.ServiceUnavailable => AssistantProblem.Unavailable,
            HttpStatusCode.TooManyRequests => AssistantProblem.RateLimited,
            HttpStatusCode.BadRequest => AssistantProblem.BadRequest,
            _ => AssistantProblem.Failed,
        },
    };

    private static string Body(IReadOnlyList<AssistantMessage> messages)
    {
        var lines = new JsonArray();
        foreach (var message in messages)
        {
            lines.Add(new JsonObject
            {
                ["role"] = message.Role == AssistantRole.Model ? "model" : "user",
                ["text"] = message.Text,
            });
        }

        return new JsonObject { ["messages"] = lines }.ToJsonString();
    }

    private static JsonObject? Parse(string text)
    {
        try
        {
            return JsonNode.Parse(text) as JsonObject;
        }
        catch (JsonException)
        {
            return null;
        }
    }
}
