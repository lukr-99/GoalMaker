using System.Globalization;
using System.Text.Json;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Tests;

/// <summary>contracts/vectors/sync-merge.json, the same file the Android tests read.</summary>
public sealed class SyncRulesContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/sync-merge.json").RootElement;

    private static RowVersion? Version(JsonElement element) =>
        element.ValueKind == JsonValueKind.Null
            ? null
            : new RowVersion(element.GetProperty("updatedAt").GetString()!, element.GetProperty("deleted").GetBoolean());

    [Fact]
    public void MergesEveryCaseAsAgreed()
    {
        var cases = vectors.GetProperty("merge").EnumerateArray().ToList();
        Assert.True(cases.Count >= 10);
        foreach (var testCase in cases)
        {
            var name = testCase.GetProperty("name").GetString();
            var decision = SyncRules.Merge(
                Version(testCase.GetProperty("local")),
                testCase.GetProperty("pending").GetBoolean(),
                Version(testCase.GetProperty("remote"))!);
            Assert.True(
                (testCase.GetProperty("result").GetString() == "take-remote") == decision.TakeRemote,
                $"{name}: take remote");
            Assert.True(testCase.GetProperty("dropPending").GetBoolean() == decision.DropPending, $"{name}: drop pending");
        }
    }

    [Fact]
    public void StartsOverOnlyWhenNeverSyncedOrMoreThan80DaysBehind()
    {
        foreach (var testCase in vectors.GetProperty("fullResync").EnumerateArray())
        {
            var now = DateTimeOffset.Parse(testCase.GetProperty("now").GetString()!, CultureInfo.InvariantCulture);
            Assert.True(
                testCase.GetProperty("full").GetBoolean() ==
                    SyncRules.NeedsFullResync(testCase.GetProperty("watermark").GetString(), now),
                testCase.GetProperty("name").GetString());
        }
    }

    [Fact]
    public void PullsFromAMinuteBeforeTheWatermark()
    {
        foreach (var testCase in vectors.GetProperty("pullFrom").EnumerateArray())
        {
            Assert.Equal(
                testCase.GetProperty("from").GetString(),
                SyncRules.PullFrom(testCase.GetProperty("watermark").GetString()));
        }
    }

    [Fact]
    public void NormalizesServerTimestampsToSortableUtcText()
    {
        foreach (var testCase in vectors.GetProperty("normalizeTimestamp").EnumerateArray())
        {
            Assert.Equal(
                testCase.GetProperty("output").GetString(),
                SyncRules.NormalizeTimestamp(testCase.GetProperty("input").GetString()!));
        }
    }
}
