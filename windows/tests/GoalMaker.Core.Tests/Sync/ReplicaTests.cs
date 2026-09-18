using System.Security.Cryptography;
using System.Text.Json.Nodes;
using GoalMaker.Infrastructure.Replica;

namespace GoalMaker.Core.Tests.Sync;

public sealed class ReplicaTests
{
    [Fact]
    public void BuiltInMigrationsAreTheRepositoryFilesByteForByte()
    {
        var folder = new DirectoryInfo(AppContext.BaseDirectory);
        while (!Directory.Exists(Path.Combine(folder!.FullName, "replica", "migrations")))
        {
            folder = folder.Parent;
        }

        var files = Directory.GetFiles(Path.Combine(folder.FullName, "replica", "migrations"), "*.sql").Order().ToList();
        var builtIn = ReplicaMigrator.BuiltIn();
        Assert.Equal(files.Select(Path.GetFileName), builtIn.Select(migration => migration.Name));
        Assert.Equal(
            files.Select(file => Convert.ToHexStringLower(SHA256.HashData(File.ReadAllBytes(file)))),
            builtIn.Select(migration => migration.Checksum));
    }

    [Fact]
    public void AChangedAppliedMigrationIsRefused()
    {
        using var test = new TestReplica();
        using var connection = new Microsoft.Data.Sqlite.SqliteConnection("Data Source=:memory:");
        connection.Open();
        var original = ReplicaMigrator.BuiltIn();
        ReplicaMigrator.Apply(connection, original);
        ReplicaMigrator.Apply(connection, original);
        var edited = original.Select(migration => migration with { Checksum = new string('0', 64) }).ToList();
        Assert.Throws<InvalidOperationException>(() => ReplicaMigrator.Apply(connection, edited));
    }

    [Fact]
    public void QueueingStoresTheRowAndOneOutboxEntryPerRow()
    {
        using var test = new TestReplica();
        var parent = test.NewTask("a", "Parent");
        test.Replica.Queue("tasks", parent);
        test.Replica.Queue("tasks", test.NewTask("b", "Child"));
        parent["title"] = "Parent, renamed";
        test.Replica.Queue("tasks", parent);

        var outbox = test.Replica.Outbox();
        Assert.Equal(["a", "b"], outbox.Select(entry => entry.RowId));
        Assert.Contains("Parent, renamed", outbox[0].Payload, StringComparison.Ordinal);
        Assert.Equal("Parent, renamed", (string?)test.Replica.Get("tasks", "a")!["title"]);
        Assert.Equal(2, test.Replica.PendingCount());
    }

    [Fact]
    public void RowsRoundTripWithTheirKinds()
    {
        using var test = new TestReplica();
        var row = test.NewTask("a", "Run");
        row["top_priority"] = true;
        row["position"] = 2.5;
        row["planned_date"] = "2026-09-19";
        test.Replica.Put("tasks", row);

        var stored = test.Replica.Get("tasks", "a")!;
        Assert.True((bool)stored["top_priority"]!);
        Assert.Equal(2.5, (double)stored["position"]!);
        Assert.Equal("2026-09-19", (string?)stored["planned_date"]);
        Assert.Null(stored["deleted_at"]);
    }

    [Fact]
    public void ACompletedPushStoresTheServersCopy()
    {
        using var test = new TestReplica();
        test.Replica.Queue("tasks", test.NewTask("a", "Run"));
        var entry = test.Replica.Outbox().Single();
        var server = test.NewTask("a", "Run");
        server["updated_at"] = "2026-09-18T10:00:00.000010Z";

        test.Replica.CompletePush(entry, server);

        Assert.Empty(test.Replica.Outbox());
        Assert.Equal("2026-09-18T10:00:00.000010Z", (string?)test.Replica.Get("tasks", "a")!["updated_at"]);
    }

    [Fact]
    public void AnEditDuringThePushIsKeptAndPushedNext()
    {
        using var test = new TestReplica();
        var row = test.NewTask("a", "Run");
        test.Replica.Queue("tasks", row);
        var entry = test.Replica.Outbox().Single();
        row["title"] = "Run 5 km";
        test.Replica.Queue("tasks", row);

        var server = test.NewTask("a", "Run");
        server["updated_at"] = "2026-09-18T10:00:00.000010Z";
        test.Replica.CompletePush(entry, server);

        Assert.Equal("Run 5 km", (string?)test.Replica.Get("tasks", "a")!["title"]);
        Assert.Single(test.Replica.Outbox());
    }

    [Fact]
    public void ClearingForAFullResyncKeepsPendingRows()
    {
        using var test = new TestReplica();
        test.Replica.Put("tasks", test.NewTask("synced", "Old"));
        test.Replica.Queue("tasks", test.NewTask("pending", "New"));
        test.Replica.SetWatermark("tasks", "2026-09-18T10:00:00.000000Z");

        test.Replica.ClearSynced("tasks");

        Assert.Null(test.Replica.Get("tasks", "synced"));
        Assert.NotNull(test.Replica.Get("tasks", "pending"));
        Assert.Null(test.Replica.Watermark("tasks"));
    }

    [Fact]
    public void ChangesAreAnnouncedAfterTheCommitOnce()
    {
        using var test = new TestReplica();
        var announced = new List<string>();
        test.Replica.Changed += (_, table) => announced.Add(table);

        test.Replica.InTransaction(() =>
        {
            test.Replica.Put("tasks", test.NewTask("a", "One"));
            test.Replica.Put("tasks", test.NewTask("b", "Two"));
            Assert.Empty(announced);
        });

        Assert.Equal(["tasks"], announced);
    }

    [Fact]
    public void AFailedTransactionLeavesNothingBehind()
    {
        using var test = new TestReplica();
        Assert.Throws<InvalidOperationException>(() => test.Replica.InTransaction(() =>
        {
            test.Replica.Queue("tasks", test.NewTask("a", "One"));
            throw new InvalidOperationException("boom");
        }));

        Assert.Null(test.Replica.Get("tasks", "a"));
        Assert.Empty(test.Replica.Outbox());
    }

    [Fact]
    public void SignOutEmptiesEverything()
    {
        using var test = new TestReplica();
        test.Replica.Queue("tasks", test.NewTask("a", "One"));
        test.Replica.SetWatermark("tasks", "2026-09-18T10:00:00.000000Z");

        test.Replica.ClearAll();

        Assert.Empty(test.Replica.All("tasks"));
        Assert.Empty(test.Replica.Outbox());
        Assert.Null(test.Replica.Watermark("tasks"));
    }

    [Fact]
    public void UnknownTablesAreRefusedInsteadOfBecomingSql()
    {
        using var test = new TestReplica();
        Assert.Throws<KeyNotFoundException>(() => test.Replica.Get("tasks; DROP TABLE tasks", "a"));
        Assert.Throws<KeyNotFoundException>(() => test.Replica.Put("nope", new JsonObject()));
    }
}
