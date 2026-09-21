using System.Net.Http.Json;
using System.Text.Json.Nodes;
using GoalMaker.Core.Auth;

namespace GoalMaker.Infrastructure.Auth;

/// <summary>
/// Reads the sign-in code out of the mailbox the local Supabase stack catches its mail in
/// (docs/sign-in.md). Dev builds only, and only against that stack, which
/// <see cref="DevSignIn.MailboxOf"/> decides. Nothing here ever reaches the cloud project, and a mailbox
/// that is not there, is slow, or holds no code is simply no code: the owner types it themselves.
/// </summary>
public sealed class LocalMailbox(HttpClient http, string mailbox)
{
    /// <summary>The code in the newest message to <paramref name="email"/>, or null.</summary>
    public async Task<string?> CodeForAsync(string email, CancellationToken cancellationToken)
    {
        try
        {
            var inbox = await http.GetFromJsonAsync<JsonNode>($"{mailbox}/api/v1/messages?limit=20", cancellationToken)
                .ConfigureAwait(false);
            if (inbox?["messages"] is not JsonArray messages)
            {
                return null;
            }

            foreach (var message in messages.OfType<JsonObject>().Where(message => IsFor(message, email)))
            {
                if (await CodeInAsync(message, cancellationToken).ConfigureAwait(false) is { } code)
                {
                    return code;
                }
            }

            return null;
        }
        catch (Exception error) when (error is HttpRequestException or TaskCanceledException or NotSupportedException or System.Text.Json.JsonException)
        {
            return null;
        }
    }

    private static bool IsFor(JsonObject message, string email) =>
        message["To"] is JsonArray addressees && addressees.OfType<JsonObject>().Any(addressee =>
            string.Equals((string?)addressee["Address"], email, StringComparison.OrdinalIgnoreCase));

    private async Task<string?> CodeInAsync(JsonObject message, CancellationToken cancellationToken)
    {
        if (DevSignIn.CodeIn((string?)message["Snippet"]) is { } fromList)
        {
            return fromList;
        }

        var id = (string?)message["ID"];
        if (id is null)
        {
            return null;
        }

        var body = await http.GetFromJsonAsync<JsonNode>($"{mailbox}/api/v1/message/{id}", cancellationToken)
            .ConfigureAwait(false);
        return DevSignIn.CodeIn((string?)body?["Text"]) ?? DevSignIn.CodeIn((string?)body?["HTML"]);
    }
}
