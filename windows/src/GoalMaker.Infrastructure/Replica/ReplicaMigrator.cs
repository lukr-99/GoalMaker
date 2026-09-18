using System.Globalization;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using Microsoft.Data.Sqlite;

namespace GoalMaker.Infrastructure.Replica;

/// <summary>
/// Applies replica/migrations the way tools/migrations.py does (ADR 0007): a schema_migrations record
/// with each file's SHA-256, every file in its own transaction, and a refusal when an applied file
/// changed or is missing.
/// </summary>
public static partial class ReplicaMigrator
{
    private const string ResourcePrefix = "GoalMaker.Replica.Migrations.";

    /// <summary>The migrations built into this assembly, in order.</summary>
    public static IReadOnlyList<ReplicaMigration> BuiltIn()
    {
        var assembly = typeof(ReplicaMigrator).Assembly;
        return [.. assembly.GetManifestResourceNames()
            .Where(name => name.StartsWith(ResourcePrefix, StringComparison.Ordinal))
            .Select(name => Read(assembly, name))
            .OrderBy(migration => migration.Number)];
    }

    public static void Apply(SqliteConnection connection, IReadOnlyList<ReplicaMigration> migrations)
    {
        for (var index = 0; index < migrations.Count; index++)
        {
            if (migrations[index].Number != index + 1)
            {
                throw new InvalidOperationException("Replica migrations must run 0001..N without gaps.");
            }
        }

        Execute(connection, "PRAGMA foreign_keys = ON");
        Execute(connection, """
            CREATE TABLE IF NOT EXISTS schema_migrations (
                number INTEGER PRIMARY KEY,
                filename TEXT NOT NULL UNIQUE,
                checksum_sha256 TEXT NOT NULL,
                applied_at_utc TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);

        var applied = new Dictionary<int, (string Name, string Checksum)>();
        using (var query = connection.CreateCommand())
        {
            query.CommandText = "SELECT number, filename, checksum_sha256 FROM schema_migrations ORDER BY number";
            using var reader = query.ExecuteReader();
            while (reader.Read())
            {
                applied[reader.GetInt32(0)] = (reader.GetString(1), reader.GetString(2));
            }
        }

        var known = migrations.ToDictionary(migration => migration.Number);
        foreach (var (number, record) in applied)
        {
            if (!known.TryGetValue(number, out var migration))
            {
                throw new InvalidOperationException($"The replica has migration {number:D4} ({record.Name}), which this app doesn't know.");
            }

            if (record.Name != migration.Name || record.Checksum != migration.Checksum)
            {
                throw new InvalidOperationException($"Applied migration {migration.Name} differs from the built-in file.");
            }
        }

        foreach (var migration in migrations.Where(migration => !applied.ContainsKey(migration.Number)))
        {
            using var transaction = connection.BeginTransaction();
            using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = migration.Sql;
            command.ExecuteNonQuery();
            command.CommandText = "INSERT INTO schema_migrations(number, filename, checksum_sha256) VALUES ($number, $name, $checksum)";
            command.Parameters.AddWithValue("$number", migration.Number);
            command.Parameters.AddWithValue("$name", migration.Name);
            command.Parameters.AddWithValue("$checksum", migration.Checksum);
            command.ExecuteNonQuery();
            transaction.Commit();
        }
    }

    public static ReplicaMigration FromBytes(string fileName, byte[] bytes)
    {
        var match = FileName().Match(fileName);
        if (!match.Success)
        {
            throw new InvalidOperationException($"{fileName} is not named 0001_description.sql");
        }

        return new ReplicaMigration(
            int.Parse(match.Groups[1].Value, CultureInfo.InvariantCulture),
            fileName,
            new UTF8Encoding(false).GetString(bytes).TrimStart('\uFEFF'),
            Convert.ToHexStringLower(SHA256.HashData(bytes)));
    }

    private static ReplicaMigration Read(Assembly assembly, string resource)
    {
        using var stream = assembly.GetManifestResourceStream(resource)!;
        using var memory = new MemoryStream();
        stream.CopyTo(memory);
        return FromBytes(resource[ResourcePrefix.Length..], memory.ToArray());
    }

    private static void Execute(SqliteConnection connection, string sql)
    {
        using var command = connection.CreateCommand();
        command.CommandText = sql;
        command.ExecuteNonQuery();
    }

    [GeneratedRegex(@"^([0-9]{4})_[a-z][a-z0-9_]*\.sql\z", RegexOptions.CultureInvariant)]
    private static partial Regex FileName();
}
