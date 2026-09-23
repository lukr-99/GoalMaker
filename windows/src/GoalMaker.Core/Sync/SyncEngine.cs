using System.Text.Json.Nodes;

namespace GoalMaker.Core.Sync;

/// <summary>
/// One sync run: push the outbox in order, then pull every table and merge (docs/sync.md). Decisions
/// come from <see cref="SyncRules"/>, so both apps behave the same. Not thread-safe: the
/// <see cref="SyncCoordinator"/> runs one at a time.
/// <para>
/// <paramref name="pulls"/> is false only for a dev build's local-only remote, which never has anything
/// to send back: a table that has never pulled has no watermark, and no watermark means a full resync,
/// which clears the table first, so pulling from it would wipe the replica on every run.
/// </para>
/// </summary>
public sealed class SyncEngine(SyncedTableCatalog catalog, IReplica replica, IRemoteTables remote, TimeProvider time, bool pulls = true)
{
    public const int PageSize = 500;

    public async Task<SyncReport> RunAsync(CancellationToken cancellationToken)
    {
        var pushed = 0;
        var rejected = 0;
        var pulled = 0;
        try
        {
            foreach (var entry in replica.Outbox())
            {
                var table = catalog[entry.Entity];
                try
                {
                    // The server owns updated_at (docs/sync.md), so it is never sent.
                    var row = JsonNode.Parse(entry.Payload)!.AsObject();
                    row.Remove(SyncedTable.UpdatedAt);
                    var stored = await remote.UpsertAsync(entry.Entity, row, cancellationToken).ConfigureAwait(false);
                    replica.CompletePush(entry, Normalize(table, stored));
                    pushed++;
                }
                catch (RemoteRejectedException error)
                {
                    replica.FailPush(entry, error.Message);
                    rejected++;
                }
            }

            if (pulls)
            {
                foreach (var table in catalog.Tables)
                {
                    pulled += await PullAsync(table, cancellationToken).ConfigureAwait(false);
                }
            }

            return new SyncReport(pushed, rejected, pulled, Offline: false, Problem: rejected > 0 ? $"{rejected} change(s) refused by the server" : null);
        }
        catch (RemoteUnavailableException error)
        {
            return new SyncReport(pushed, rejected, pulled, Offline: true, Problem: error.Message);
        }
    }

    /// <summary>Copies server timestamps into the normalized form the replica compares as text.</summary>
    public static JsonObject Normalize(SyncedTable table, JsonObject row)
    {
        var copy = new JsonObject();
        foreach (var column in table.Columns)
        {
            var value = row[column.Name];
            if (column.Kind == ColumnKind.Timestamp && value is JsonValue text && text.TryGetValue<string>(out var raw))
            {
                copy[column.Name] = SyncRules.NormalizeTimestamp(raw) ?? raw;
            }
            else
            {
                copy[column.Name] = value?.DeepClone();
            }
        }

        return copy;
    }

    private async Task<int> PullAsync(SyncedTable table, CancellationToken cancellationToken)
    {
        var watermark = replica.Watermark(table.Name);
        var full = SyncRules.NeedsFullResync(watermark, time.GetUtcNow());
        if (full)
        {
            replica.ClearSynced(table.Name);
            watermark = null;
        }

        var from = full ? null : SyncRules.PullFrom(watermark);
        RowCursor? cursor = null;
        var newest = watermark;
        var count = 0;
        while (true)
        {
            var page = await remote.PullAsync(table.Name, from, cursor, PageSize, cancellationToken).ConfigureAwait(false);
            if (page.Count == 0)
            {
                break;
            }

            var rows = page.Select(row => Normalize(table, row)).ToList();
            replica.InTransaction(() =>
            {
                foreach (var row in rows)
                {
                    Merge(table.Name, row);
                }
            });
            count += rows.Count;

            var last = rows[^1];
            var lastUpdated = (string)last[SyncedTable.UpdatedAt]!;
            if (newest is null || string.CompareOrdinal(lastUpdated, newest) > 0)
            {
                newest = lastUpdated;
            }

            if (page.Count < PageSize)
            {
                break;
            }

            cursor = new RowCursor(lastUpdated, (string)last[SyncedTable.Id]!);
        }

        if (newest is not null)
        {
            replica.SetWatermark(table.Name, newest);
        }

        return count;
    }

    private void Merge(string table, JsonObject remoteRow)
    {
        var id = (string)remoteRow[SyncedTable.Id]!;
        var local = replica.Get(table, id);
        var decision = SyncRules.Merge(
            local is null ? null : VersionOf(local),
            replica.IsPending(table, id),
            VersionOf(remoteRow));
        if (decision.DropPending)
        {
            replica.DropPending(table, id);
        }

        if (decision.TakeRemote)
        {
            replica.Put(table, remoteRow);
        }
    }

    private static RowVersion VersionOf(JsonObject row) =>
        new((string?)row[SyncedTable.UpdatedAt] ?? string.Empty, row[SyncedTable.DeletedAt] is not null);
}
