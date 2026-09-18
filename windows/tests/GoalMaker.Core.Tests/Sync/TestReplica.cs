using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;
using GoalMaker.Infrastructure.Replica;
using GoalMaker.Infrastructure.Sync;

namespace GoalMaker.Core.Tests.Sync;

/// <summary>A real <see cref="SqliteReplica"/> on a throwaway file, with the built-in schema and contract.</summary>
internal sealed class TestReplica : IDisposable
{
    private readonly string folder = Path.Combine(Path.GetTempPath(), "goalmaker-tests", Guid.NewGuid().ToString("N"));

    public TestReplica()
    {
        Catalog = ContractResources.SyncedTables();
        Replica = new SqliteReplica(Path.Combine(folder, "replica.db"), Catalog, ReplicaMigrator.BuiltIn());
    }

    public SyncedTableCatalog Catalog { get; }

    public SqliteReplica Replica { get; }

    public const string Owner = "11111111-1111-1111-1111-111111111111";

    /// <summary>A complete tasks row as a device would create it.</summary>
    public JsonObject NewTask(string id, string title, string createdAt = "2026-09-18T08:00:00.000000Z")
    {
        var row = new JsonObject();
        foreach (var column in Catalog["tasks"].Columns)
        {
            row[column.Name] = null;
        }

        row["id"] = id;
        row["owner_id"] = Owner;
        row["title"] = title;
        row["notes"] = string.Empty;
        row["top_priority"] = false;
        row["status"] = "open";
        row["position"] = 0.0;
        row["created_at"] = createdAt;
        row["updated_at"] = string.Empty;
        return row;
    }

    public void Dispose()
    {
        Replica.Dispose();
        Microsoft.Data.Sqlite.SqliteConnection.ClearAllPools();
        try
        {
            Directory.Delete(folder, recursive: true);
        }
        catch (IOException)
        {
            // Best effort on Windows; the temp folder is cleaned eventually.
        }
    }
}
