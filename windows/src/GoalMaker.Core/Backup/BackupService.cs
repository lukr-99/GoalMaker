using System.Text.Json;
using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Backup;

/// <summary>
/// Reading the owner's data out to a file and back in (docs/backup.md, spec story 91). The rules
/// live in <see cref="BackupRules"/>; this is the part that touches the replica.
/// </summary>
public sealed class BackupService(
    SyncedTableCatalog catalog,
    IReplica replica,
    Func<string?> ownerId,
    string appVersion,
    string app,
    TimeProvider time)
{
    /// <summary>What the export would carry, or null when nobody is signed in. Tombstones are left out.</summary>
    public BackupDocument? Read()
    {
        if (ownerId() is not { } owner)
        {
            return null;
        }

        var tables = new Dictionary<string, IReadOnlyList<JsonObject>>(StringComparer.Ordinal);
        foreach (var table in catalog.Tables)
        {
            tables[table.Name] = [.. replica.All(table.Name).Where(row => row[SyncedTable.DeletedAt] is null)];
        }

        return new BackupDocument(
            BackupRules.Version,
            SyncRules.Format(time.GetUtcNow()),
            app,
            appVersion,
            owner,
            tables);
    }

    /// <summary>The file's text, or null when nobody is signed in.</summary>
    public string? Export() =>
        Read() is { } document ? BackupRules.Write(document, [.. catalog.Tables.Select(table => table.Name)]) : null;

    /// <summary>What is wrong with this text for the signed-in owner, or null when it can be restored.</summary>
    public BackupProblem? Check(string text)
    {
        if (ownerId() is not { } owner)
        {
            return BackupProblem.AnotherOwner;
        }

        return Parse(text) is { } root
            ? BackupRules.Check(root, owner, catalog.Tables.Select(table => table.Name).ToHashSet(StringComparer.Ordinal))
            : BackupProblem.NotABackup;
    }

    /// <summary>What restoring this text would do, without doing it. Null when the file would be refused.</summary>
    public RestoreReport? Preview(string text) => Weigh(text)?.Report;

    /// <summary>
    /// Restores the file: every row it is newer for, in one transaction, queued so the rows reach
    /// the other devices too. Null when the file is refused, in which case nothing was written.
    /// </summary>
    public RestoreReport? Restore(string text, Action? requestSync = null)
    {
        if (Weigh(text) is not { } weighed)
        {
            return null;
        }

        if (weighed.Changes.Count > 0)
        {
            replica.InTransaction(() =>
            {
                foreach (var (table, row) in weighed.Changes)
                {
                    replica.Queue(table, row);
                }
            });
            requestSync?.Invoke();
        }

        return weighed.Report;
    }

    // The decision for every row in the file: what it would do, and the rows that would be written.
    private (RestoreReport Report, List<(string Table, JsonObject Row)> Changes)? Weigh(string text)
    {
        if (Check(text) is not null || Parse(text) is not { } root || BackupRules.Read(root) is not { } document)
        {
            return null;
        }

        var report = new RestoreReport();
        var changes = new List<(string, JsonObject)>();
        foreach (var table in catalog.Tables)
        {
            if (!document.Tables.TryGetValue(table.Name, out var rows))
            {
                continue;
            }

            foreach (var row in rows)
            {
                if (Text(row[SyncedTable.Id]) is not { } id)
                {
                    continue;
                }

                var local = replica.Get(table.Name, id);
                if (local is null)
                {
                    report += new RestoreReport(Added: 1);
                    changes.Add((table.Name, row));
                }
                else if (BackupRules.TakesFile(Text(local[SyncedTable.UpdatedAt]), Text(row[SyncedTable.UpdatedAt])))
                {
                    report += new RestoreReport(Updated: 1);
                    changes.Add((table.Name, row));
                }
                else
                {
                    report += new RestoreReport(Kept: 1);
                }
            }
        }

        return (report, changes);
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

    private static string? Text(JsonNode? node) =>
        node is JsonValue value && value.TryGetValue<string>(out var text) && text.Length > 0 ? text : null;
}
