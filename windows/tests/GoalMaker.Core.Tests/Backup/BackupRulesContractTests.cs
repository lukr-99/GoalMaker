using System.Text.Json;
using System.Text.Json.Nodes;
using GoalMaker.Core.Backup;

namespace GoalMaker.Core.Tests.Backup;

/// <summary>contracts/vectors/backup.json, the same file the Android tests read.</summary>
public sealed class BackupRulesContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/backup.json").RootElement;

    private IReadOnlyList<string> Order() =>
        [.. vectors.GetProperty("order").EnumerateArray().Select(table => table.GetString()!)];

    [Fact]
    public void ThisBuildWritesTheFormatAndVersionTheContractNames()
    {
        var current = vectors.GetProperty("current");
        Assert.Equal(BackupRules.Format, current.GetProperty("format").GetString());
        Assert.Equal(BackupRules.Version, current.GetProperty("version").GetInt32());
    }

    [Fact]
    public void EveryCheckReachesTheContractsVerdict()
    {
        var known = Order().ToHashSet(StringComparer.Ordinal);
        foreach (var testCase in vectors.GetProperty("checks").EnumerateArray())
        {
            var name = testCase.GetProperty("name").GetString();
            var expectedProperty = testCase.GetProperty("expect");
            var expected = expectedProperty.ValueKind == JsonValueKind.Null ? null : expectedProperty.GetString();

            var problem = BackupRules.Check(
                (JsonObject)JsonNode.Parse(testCase.GetProperty("file").GetRawText())!,
                testCase.GetProperty("owner").GetString()!,
                known);

            Assert.Equal(name + ": " + expected, name + ": " + Key(problem));
        }
    }

    [Fact]
    public void EveryRowDecisionFollowsTheContract()
    {
        foreach (var testCase in vectors.GetProperty("takesFile").EnumerateArray())
        {
            string? Stamp(string key) =>
                testCase.GetProperty(key).ValueKind == JsonValueKind.Null ? null : testCase.GetProperty(key).GetString();

            var name = testCase.GetProperty("name").GetString();
            Assert.Equal(
                name + ": " + testCase.GetProperty("expect").GetBoolean(),
                name + ": " + BackupRules.TakesFile(Stamp("local"), Stamp("file")));
        }
    }

    [Fact]
    public void TheGoldenExportIsWrittenAndReadBackExactly()
    {
        var golden = vectors.GetProperty("golden");
        var document = (JsonObject)JsonNode.Parse(golden.GetProperty("document").GetRawText())!;
        var expected = golden.GetProperty("text").GetString()!;

        var read = BackupRules.Read(document)!;
        Assert.Equal("1.0.0", read.AppVersion);
        Assert.Equal(2, read.RowCount);
        Assert.Null(BackupRules.Check(document, read.Owner, Order().ToHashSet(StringComparer.Ordinal)));

        Assert.Equal(expected, BackupRules.Write(read, Order()));
        Assert.Equal(read.RowCount, BackupRules.Read(JsonNode.Parse(expected))!.RowCount);
    }

    [Fact]
    public void AnExportCarriesTheSyncedTablesInTheirOwnOrder()
    {
        var tables = ContractFiles.Load("schemas/synced-tables.json").RootElement
            .GetProperty("tables").EnumerateArray().Select(table => table.GetProperty("name").GetString()!);

        Assert.Equal(tables, Order());
    }

    [Fact]
    public void AFileIsNamedAfterTheDayItWasWritten() =>
        Assert.Equal("goalmaker-2026-09-20.json", BackupRules.FileName("2026-09-20"));

    // The name a problem has in the vector file: the enum in camel case, or null when there is none.
    private static string? Key(BackupProblem? problem) =>
        problem is null ? null : char.ToLowerInvariant(problem.ToString()![0]) + problem.ToString()![1..];
}
