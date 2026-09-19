using System.Text;
using System.Text.Json.Nodes;
using GoalMaker.Core.Settings;
using GoalMaker.Infrastructure.Postgrest;

namespace GoalMaker.Infrastructure.Settings;

/// <summary><see cref="IProfileSettings"/> over PostgREST; row security limits the update to the owner's own profile.</summary>
public sealed class PostgrestProfileSettings(PostgrestHttp postgrest, Func<string?> userId) : IProfileSettings
{
    public async Task UpdateAsync(string timeZone, int dayStartHour, CancellationToken cancellationToken = default)
    {
        if (userId() is not { Length: > 0 } id)
        {
            return;
        }

        using var request = postgrest.Request(HttpMethod.Patch, $"profiles?id=eq.{Uri.EscapeDataString(id)}");
        request.Headers.Add("Prefer", "return=minimal");
        request.Content = new StringContent(
            new JsonObject { ["time_zone"] = timeZone, ["day_rollover_hour"] = dayStartHour }.ToJsonString(),
            Encoding.UTF8,
            "application/json");
        await postgrest.SendAsync(request, cancellationToken).ConfigureAwait(false);
    }
}
