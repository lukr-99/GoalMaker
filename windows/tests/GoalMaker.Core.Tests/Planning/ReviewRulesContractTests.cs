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

    private static DateOnly Day(JsonElement testCase, string name) =>
        DateOnly.ParseExact(testCase.GetProperty(name).GetString()!, "yyyy-MM-dd", CultureInfo.InvariantCulture);
}
