using System.Globalization;
using System.Text;
using System.Text.Json.Nodes;
using GoalMaker.Core.Activity;
using GoalMaker.Core.Sync;
using GoalMaker.Infrastructure.Postgrest;

namespace GoalMaker.Infrastructure.Activity;

/// <summary><see cref="IActivityLog"/> over PostgREST: the <c>activity_log</c> table and the <c>undo_activity</c> function (0007).</summary>
public sealed class PostgrestActivityLog(PostgrestHttp postgrest) : IActivityLog
{
    public async Task<IReadOnlyList<ActivityEntry>> RecentAsync(int limit = 60, CancellationToken cancellationToken = default)
    {
        using var request = postgrest.Request(
            HttpMethod.Get,
            $"activity_log?select=id,entity,entity_id,action,actor,before,after,created_at,undone_at&order=id.desc&limit={limit}");
        var text = await postgrest.SendAsync(request, cancellationToken).ConfigureAwait(false);
        return [.. (JsonNode.Parse(text) as JsonArray ?? []).OfType<JsonObject>().Select(row => new ActivityEntry(
            (long)row["id"]!,
            (string)row["entity"]!,
            (string)row["entity_id"]!,
            (string)row["action"]!,
            (string)row["actor"]!,
            row["before"] as JsonObject,
            row["after"] as JsonObject ?? [],
            Instant((string?)row["created_at"]) ?? DateTimeOffset.UnixEpoch,
            Instant((string?)row["undone_at"])))];
    }

    public async Task<UndoOutcome> UndoAsync(long entryId, CancellationToken cancellationToken = default)
    {
        using var request = postgrest.Request(HttpMethod.Post, "rpc/undo_activity");
        request.Content = new StringContent(new JsonObject { ["entry_id"] = entryId }.ToJsonString(), Encoding.UTF8, "application/json");
        try
        {
            await postgrest.SendAsync(request, cancellationToken).ConfigureAwait(false);
            return UndoOutcome.Undone;
        }
        catch (RemoteRejectedException refused)
        {
            return refused.Code switch
            {
                "40001" => UndoOutcome.ChangedSince,
                "55000" => UndoOutcome.AlreadyUndone,
                _ => UndoOutcome.NotPossible,
            };
        }
    }

    private static DateTimeOffset? Instant(string? text) =>
        text is not null && DateTimeOffset.TryParse(text, CultureInfo.InvariantCulture, DateTimeStyles.AssumeUniversal, out var instant)
            ? instant
            : null;
}
