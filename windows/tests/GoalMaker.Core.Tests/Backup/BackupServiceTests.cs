using System.Text.Json.Nodes;
using GoalMaker.Core.Backup;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Sync;
using GoalMaker.Core.Tests.Sync;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.Core.Tests.Backup;

/// <summary>Export and restore against a real replica (M6-03): what a file carries and what it does coming back.</summary>
public sealed class BackupServiceTests : IDisposable
{
    private static readonly DateOnly Day = new(2026, 9, 18);
    private readonly TestReplica test = new();
    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 9, 18, 12, 0, 0, TimeSpan.Zero));
    private readonly TaskList tasks;
    private readonly BackupService backup;

    public BackupServiceTests()
    {
        var rows = new NewRows(test.Catalog, () => TestReplica.Owner, time);
        var areas = new AreaList(test.Replica, rows, ["violet", "blue"], () => { });
        tasks = new TaskList(
            test.Replica, rows, areas, new TagList(test.Replica, rows, () => { }),
            new ProjectList(test.Replica, rows, () => { }), () => { }, () => Day);
        backup = new BackupService(test.Catalog, test.Replica, () => TestReplica.Owner, "1.0.0", "windows", time);
    }

    public void Dispose() => test.Dispose();

    private TaskItem Add(string line) =>
        tasks.Add(ComposerParser.Parse(line, new DateTime(2026, 9, 18, 9, 0, 0)))!;

    [Fact]
    public void AnExportCarriesTheOwnersRowsAndLeavesTombstonesOut()
    {
        var kept = Add("Call the bank @Health");
        var gone = Add("Old thing");
        tasks.Delete(gone.Id);

        var text = backup.Export()!;

        Assert.StartsWith("{\n  \"format\": \"goalmaker.backup\"", text, StringComparison.Ordinal);
        Assert.Contains(kept.Id, text, StringComparison.Ordinal);
        Assert.DoesNotContain(gone.Id, text, StringComparison.Ordinal);
        Assert.Contains("\"windows\"", text, StringComparison.Ordinal);
    }

    [Fact]
    public void AFileFromTheOtherAppRestoresIntoAnEmptyReplicaRowForRow()
    {
        Add("Call the bank 17:00 @Health #errand");
        Add("Water the plants");
        var text = backup.Export()!;
        var before = test.Catalog.Tables.ToDictionary(table => table.Name, table => test.Replica.All(table.Name).Count);

        test.Replica.ClearAll();

        var report = backup.Restore(text)!;

        Assert.Equal(0, report.Kept);
        Assert.Equal(0, report.Updated);
        Assert.Equal(before.Values.Sum(), report.Added);
        Assert.Equal(before, test.Catalog.Tables.ToDictionary(table => table.Name, table => test.Replica.All(table.Name).Count));
        Assert.Equal(report.Added, test.Replica.Outbox().Count);
    }

    [Fact]
    public void ARestoreNeverUndoesNewerWorkAndNeverDeletesWhatTheFileLacks()
    {
        var task = Add("Call the bank");
        var text = backup.Export()!;
        tasks.Rename(task.Id, "Call the bank about the fee");
        Stamp(task.Id, "2026-09-19T08:00:00.000000Z");
        var later = Add("Made after the export");

        var report = backup.Restore(text)!;

        Assert.Equal(0, report.Updated);
        Assert.True(report.Kept > 0);
        Assert.Equal("Call the bank about the fee", tasks.Find(task.Id)!.Title);
        Assert.NotNull(tasks.Find(later.Id));
    }

    [Fact]
    public void AFileThatIsNewerWins()
    {
        var task = Add("Call the bank");
        Stamp(task.Id, "2026-09-20T08:00:00.000000Z");
        var text = backup.Export()!;
        tasks.Rename(task.Id, "Older title");
        Stamp(task.Id, "2026-09-19T08:00:00.000000Z");

        Assert.Equal(1, backup.Restore(text)!.Updated);
        Assert.Equal("Call the bank", tasks.Find(task.Id)!.Title);
    }

    [Fact]
    public void APreviewSaysWhatARestoreWouldDoAndChangesNothing()
    {
        Add("Call the bank");
        var text = backup.Export()!;
        test.Replica.ClearAll();

        Assert.True(backup.Preview(text)!.Added > 0);
        Assert.Empty(test.Replica.All("tasks"));
        Assert.Empty(test.Replica.Outbox());
    }

    [Theory]
    [InlineData("not json at all", BackupProblem.NotABackup)]
    [InlineData("version", BackupProblem.TooNew)]
    [InlineData("owner", BackupProblem.AnotherOwner)]
    [InlineData("table", BackupProblem.UnknownTable)]
    public void ARefusedFileWritesNothingAndSaysWhy(string spoil, BackupProblem expected)
    {
        Add("Call the bank");
        var text = backup.Export()!;
        text = spoil switch
        {
            "version" => text.Replace("\"version\": 1", "\"version\": 99", StringComparison.Ordinal),
            "owner" => text.Replace(TestReplica.Owner, "22222222-2222-4222-8222-222222222222", StringComparison.Ordinal),
            "table" => text.Replace("\"areas\": [", "\"sprints\": [", StringComparison.Ordinal),
            _ => spoil,
        };
        test.Replica.ClearAll();

        Assert.Equal(expected, backup.Check(text));
        Assert.Null(backup.Restore(text));
        Assert.Empty(test.Replica.All("tasks"));
    }

    // A server timestamp on a row, the way a pull would leave it.
    private void Stamp(string id, string updatedAt)
    {
        var row = test.Replica.Get("tasks", id)!;
        row[SyncedTable.UpdatedAt] = updatedAt;
        test.Replica.Put("tasks", row);
    }
}
