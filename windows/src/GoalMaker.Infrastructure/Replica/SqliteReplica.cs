using System.Globalization;
using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;
using Microsoft.Data.Sqlite;

namespace GoalMaker.Infrastructure.Replica;

/// <summary>
/// <see cref="IReplica"/> on one SQLite file with the shared schema (ADR 0007). SQL is built from the
/// synced-table contract, so a new column needs no code here. All access is serialized; transactions
/// nest, and change events fire after the outermost commit.
/// </summary>
public sealed class SqliteReplica : IReplica, IDisposable
{
    private readonly SyncedTableCatalog catalog;
    private readonly SqliteConnection connection;
    private readonly Lock gate = new();
    private readonly HashSet<string> changedInTransaction = [];
    private SqliteTransaction? transaction;
    private int depth;

    public SqliteReplica(string path, SyncedTableCatalog catalog, IReadOnlyList<ReplicaMigration> migrations)
    {
        this.catalog = catalog;
        Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(path))!);
        connection = new SqliteConnection(new SqliteConnectionStringBuilder { DataSource = path, Pooling = false }.ToString());
        connection.Open();
        Execute("PRAGMA journal_mode = WAL");
        ReplicaMigrator.Apply(connection, migrations);
    }

    public event EventHandler<string>? Changed;

    public JsonObject? Get(string table, string id)
    {
        lock (gate)
        {
            using var command = Command($"SELECT * FROM {Checked(table)} WHERE id = $id");
            command.Parameters.AddWithValue("$id", id);
            using var reader = command.ExecuteReader();
            return reader.Read() ? ReadRow(catalog[table], reader) : null;
        }
    }

    public IReadOnlyList<JsonObject> All(string table)
    {
        lock (gate)
        {
            using var command = Command($"SELECT * FROM {Checked(table)}");
            using var reader = command.ExecuteReader();
            var rows = new List<JsonObject>();
            while (reader.Read())
            {
                rows.Add(ReadRow(catalog[table], reader));
            }

            return rows;
        }
    }

    public void Queue(string table, JsonObject row) => InTransaction(() =>
    {
        Put(table, row);
        var id = (string)row[SyncedTable.Id]!;
        var payload = row.ToJsonString();
        // One entry per row, kept at its first position so parents still push before children.
        using var update = Command("UPDATE outbox SET payload = $payload, attempts = 0, last_error = NULL WHERE entity = $entity AND row_id = $id");
        update.Parameters.AddWithValue("$payload", payload);
        update.Parameters.AddWithValue("$entity", table);
        update.Parameters.AddWithValue("$id", id);
        if (update.ExecuteNonQuery() == 0)
        {
            using var insert = Command("INSERT INTO outbox(entity, row_id, payload, queued_at) VALUES ($entity, $id, $payload, $queued)");
            insert.Parameters.AddWithValue("$entity", table);
            insert.Parameters.AddWithValue("$id", id);
            insert.Parameters.AddWithValue("$payload", payload);
            insert.Parameters.AddWithValue("$queued", DateTimeOffset.UtcNow.ToString("O", CultureInfo.InvariantCulture));
            insert.ExecuteNonQuery();
        }
    });

    public bool IsPending(string table, string id)
    {
        lock (gate)
        {
            using var command = Command("SELECT 1 FROM outbox WHERE entity = $entity AND row_id = $id LIMIT 1");
            command.Parameters.AddWithValue("$entity", table);
            command.Parameters.AddWithValue("$id", id);
            return command.ExecuteScalar() is not null;
        }
    }

    public int PendingCount()
    {
        lock (gate)
        {
            using var command = Command("SELECT COUNT(*) FROM outbox");
            return Convert.ToInt32(command.ExecuteScalar(), CultureInfo.InvariantCulture);
        }
    }

    public IReadOnlyList<OutboxEntry> Outbox()
    {
        lock (gate)
        {
            using var command = Command("SELECT seq, entity, row_id, payload, attempts, last_error FROM outbox ORDER BY seq");
            using var reader = command.ExecuteReader();
            var entries = new List<OutboxEntry>();
            while (reader.Read())
            {
                entries.Add(new OutboxEntry(
                    reader.GetInt64(0),
                    reader.GetString(1),
                    reader.GetString(2),
                    reader.GetString(3),
                    reader.GetInt32(4),
                    reader.IsDBNull(5) ? null : reader.GetString(5)));
            }

            return entries;
        }
    }

    public void CompletePush(OutboxEntry entry, JsonObject serverRow) => InTransaction(() =>
    {
        using var current = Command("SELECT payload FROM outbox WHERE seq = $seq");
        current.Parameters.AddWithValue("$seq", entry.Seq);
        if (current.ExecuteScalar() as string != entry.Payload)
        {
            // Edited again while the push was in flight: keep the newer local row and push it next.
            return;
        }

        Put(entry.Entity, serverRow);
        using var delete = Command("DELETE FROM outbox WHERE seq = $seq");
        delete.Parameters.AddWithValue("$seq", entry.Seq);
        delete.ExecuteNonQuery();
    });

    public void FailPush(OutboxEntry entry, string error)
    {
        lock (gate)
        {
            using var command = Command("UPDATE outbox SET attempts = attempts + 1, last_error = $error WHERE seq = $seq");
            command.Parameters.AddWithValue("$error", error);
            command.Parameters.AddWithValue("$seq", entry.Seq);
            command.ExecuteNonQuery();
        }
    }

    public void Put(string table, JsonObject row) => InTransaction(() =>
    {
        var definition = catalog[table];
        var names = definition.Columns.Select(column => column.Name).ToList();
        using var command = Command(
            $"INSERT INTO {Checked(table)} ({string.Join(", ", names)}) VALUES ({string.Join(", ", names.Select((_, index) => $"$p{index}"))}) " +
            $"ON CONFLICT(id) DO UPDATE SET {string.Join(", ", names.Where(name => name != SyncedTable.Id).Select(name => $"{name} = excluded.{name}"))}");
        for (var index = 0; index < definition.Columns.Count; index++)
        {
            command.Parameters.AddWithValue($"$p{index}", ToSqlite(definition.Columns[index], row[definition.Columns[index].Name]));
        }

        command.ExecuteNonQuery();
        changedInTransaction.Add(table);
    });

    public void DropPending(string table, string id)
    {
        lock (gate)
        {
            using var command = Command("DELETE FROM outbox WHERE entity = $entity AND row_id = $id");
            command.Parameters.AddWithValue("$entity", table);
            command.Parameters.AddWithValue("$id", id);
            command.ExecuteNonQuery();
        }
    }

    public string? Watermark(string table)
    {
        lock (gate)
        {
            using var command = Command("SELECT watermark FROM sync_state WHERE entity = $entity");
            command.Parameters.AddWithValue("$entity", table);
            return command.ExecuteScalar() as string;
        }
    }

    public void SetWatermark(string table, string watermark)
    {
        lock (gate)
        {
            using var command = Command(
                "INSERT INTO sync_state(entity, watermark, last_pulled_at) VALUES ($entity, $watermark, $now) " +
                "ON CONFLICT(entity) DO UPDATE SET watermark = excluded.watermark, last_pulled_at = excluded.last_pulled_at");
            command.Parameters.AddWithValue("$entity", table);
            command.Parameters.AddWithValue("$watermark", watermark);
            command.Parameters.AddWithValue("$now", DateTimeOffset.UtcNow.ToString("O", CultureInfo.InvariantCulture));
            command.ExecuteNonQuery();
        }
    }

    public void ClearSynced(string table) => InTransaction(() =>
    {
        using var rows = Command($"DELETE FROM {Checked(table)} WHERE id NOT IN (SELECT row_id FROM outbox WHERE entity = $entity)");
        rows.Parameters.AddWithValue("$entity", table);
        rows.ExecuteNonQuery();
        using var watermark = Command("DELETE FROM sync_state WHERE entity = $entity");
        watermark.Parameters.AddWithValue("$entity", table);
        watermark.ExecuteNonQuery();
        changedInTransaction.Add(table);
    });

    public void ClearAll() => InTransaction(() =>
    {
        foreach (var table in catalog.Tables)
        {
            Execute($"DELETE FROM {table.Name}");
            changedInTransaction.Add(table.Name);
        }

        Execute("DELETE FROM outbox");
        Execute("DELETE FROM sync_state");
    });

    public void InTransaction(Action work)
    {
        string[] changed = [];
        lock (gate)
        {
            if (depth == 0)
            {
                transaction = connection.BeginTransaction();
            }

            depth++;
            try
            {
                work();
                depth--;
                if (depth == 0)
                {
                    transaction!.Commit();
                    changed = [.. changedInTransaction];
                    changedInTransaction.Clear();
                }
            }
            catch
            {
                depth--;
                if (depth == 0)
                {
                    transaction!.Rollback();
                    changedInTransaction.Clear();
                }

                throw;
            }
            finally
            {
                if (depth == 0)
                {
                    transaction?.Dispose();
                    transaction = null;
                }
            }
        }

        foreach (var table in changed)
        {
            Changed?.Invoke(this, table);
        }
    }

    public void Dispose()
    {
        transaction?.Dispose();
        connection.Dispose();
    }

    private static JsonObject ReadRow(SyncedTable table, SqliteDataReader reader)
    {
        var row = new JsonObject();
        foreach (var column in table.Columns)
        {
            var ordinal = reader.GetOrdinal(column.Name);
            row[column.Name] = reader.IsDBNull(ordinal)
                ? null
                : column.Kind switch
                {
                    ColumnKind.Boolean => JsonValue.Create(reader.GetInt64(ordinal) != 0),
                    ColumnKind.Integer => JsonValue.Create(reader.GetInt64(ordinal)),
                    ColumnKind.Real => JsonValue.Create(reader.GetDouble(ordinal)),
                    _ => JsonValue.Create(reader.GetString(ordinal)),
                };
        }

        return row;
    }

    private static object ToSqlite(SyncedColumn column, JsonNode? value)
    {
        if (value is null)
        {
            return DBNull.Value;
        }

        // Through the JSON text, so values parsed from the server and values made in code convert alike.
        var kind = value.GetValueKind();
        return column.Kind switch
        {
            ColumnKind.Boolean => kind == System.Text.Json.JsonValueKind.True ? 1L : 0L,
            ColumnKind.Integer => (long)decimal.Parse(value.ToJsonString(), NumberStyles.Float, CultureInfo.InvariantCulture),
            ColumnKind.Real => double.Parse(value.ToJsonString(), NumberStyles.Float, CultureInfo.InvariantCulture),
            _ => kind == System.Text.Json.JsonValueKind.String ? value.GetValue<string>() : value.ToJsonString(),
        };
    }

    private string Checked(string table) => catalog[table].Name;

    private SqliteCommand Command(string sql)
    {
        var command = connection.CreateCommand();
        command.CommandText = sql;
        command.Transaction = transaction;
        return command;
    }

    private void Execute(string sql)
    {
        using var command = Command(sql);
        command.ExecuteNonQuery();
    }
}
