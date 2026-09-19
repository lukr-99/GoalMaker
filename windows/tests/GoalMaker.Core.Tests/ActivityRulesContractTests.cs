using System.Text.Json;
using System.Text.Json.Nodes;
using GoalMaker.Core.Activity;

namespace GoalMaker.Core.Tests;

/// <summary>contracts/vectors/activity.json, the same file the Android tests read.</summary>
public sealed class ActivityRulesContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/activity.json").RootElement;

    [Fact]
    public void EveryActivityChange()
    {
        foreach (var testCase in vectors.GetProperty("cases").EnumerateArray())
        {
            var expect = testCase.GetProperty("expect");
            var expected = new ActivityChange(
                expect.GetProperty("change").GetString()!,
                expect.GetProperty("subject").GetString(),
                expect.TryGetProperty("day", out var day) ? day.GetString() : null);
            var actual = ActivityRules.Change(
                testCase.GetProperty("entity").GetString()!,
                testCase.GetProperty("action").GetString()!,
                JsonNode.Parse(testCase.GetProperty("before").GetRawText()) as JsonObject,
                (JsonObject)JsonNode.Parse(testCase.GetProperty("after").GetRawText())!);
            Assert.True(expected == actual, $"{testCase.GetProperty("name").GetString()}: expected {expected}, got {actual}");
        }
    }
}
