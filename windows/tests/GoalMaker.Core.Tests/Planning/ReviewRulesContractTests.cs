using System.Globalization;
using System.Text.Json;
using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>contracts/vectors/reviews.json, the same file the Android tests and the connector read.</summary>
public sealed class ReviewRulesContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/reviews.json").RootElement;

    [Fact]
    public void EveryReviewId()
    {
        foreach (var testCase in vectors.GetProperty("ids").EnumerateArray())
        {
            Assert.Equal(
                testCase.GetProperty("id").GetString(),
                ReviewRules.IdOf(testCase.GetProperty("owner").GetString()!, testCase.GetProperty("kind").GetString()!, Day(testCase, "periodStart")));
        }
    }

    [Fact]
    public void EveryReviewPeriod()
    {
        foreach (var testCase in vectors.GetProperty("periods").EnumerateArray())
        {
            Assert.Equal(Day(testCase, "start"), ReviewRules.PeriodStart(testCase.GetProperty("kind").GetString()!, Day(testCase, "day")));
        }
    }

    [Fact]
    public void EveryJanuaryNudge()
    {
        foreach (var testCase in vectors.GetProperty("newYear").EnumerateArray())
        {
            var expect = testCase.GetProperty("expect") is { ValueKind: JsonValueKind.Object } nudge
                ? new NewYearNudge(nudge.GetProperty("year").GetInt32(), nudge.GetProperty("review").GetBoolean(), nudge.GetProperty("goals").GetBoolean())
                : null;
            var dismissed = testCase.GetProperty("dismissedYear");
            Assert.True(
                expect == ReviewRules.NewYear(
                    Day(testCase, "today"),
                    testCase.GetProperty("yearGoals").GetInt32(),
                    testCase.GetProperty("lastYearReviewed").GetBoolean(),
                    dismissed.ValueKind == JsonValueKind.Null ? null : dismissed.GetInt32()),
                testCase.GetProperty("name").GetString());
        }
    }

    private static DateOnly Day(JsonElement testCase, string name) =>
        DateOnly.ParseExact(testCase.GetProperty(name).GetString()!, "yyyy-MM-dd", CultureInfo.InvariantCulture);
}
