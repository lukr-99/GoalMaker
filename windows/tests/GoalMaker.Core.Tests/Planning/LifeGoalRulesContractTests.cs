using System.Globalization;
using System.Text.Json;
using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>contracts/vectors/life-goals.json, the same file the Android tests and the connector read.</summary>
public sealed class LifeGoalRulesContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/life-goals.json").RootElement;

    [Fact]
    public void EveryTimeLeft()
    {
        foreach (var testCase in Cases("timeLeft"))
        {
            var expect = testCase.GetProperty("expect");
            TimeLeft? expected = expect.ValueKind == JsonValueKind.Null
                ? null
                : new TimeLeft(
                    Enum.Parse<TimeLeftUnit>(expect.GetProperty("unit").GetString()!, ignoreCase: true),
                    expect.GetProperty("count").GetInt32());
            Assert.True(expected == LifeGoalRules.TimeLeft(Day(testCase, "by"), Day(testCase, "today")!.Value), Name(testCase));
        }
    }

    [Fact]
    public void EveryOrder()
    {
        foreach (var testCase in Cases("order"))
        {
            var expected = testCase.GetProperty("expect").EnumerateArray().Select(id => id.GetString()!).ToList();
            Assert.True(expected.SequenceEqual(LifeGoalRules.Ordered(Goals(testCase)).Select(goal => goal.Id)), Name(testCase));
        }
    }

    [Fact]
    public void EveryHash()
    {
        foreach (var testCase in Cases("fnv1a"))
        {
            Assert.True(testCase.GetProperty("expect").GetUInt32() == WhyReminder.Fnv1a(testCase.GetProperty("text").GetString()!), Name(testCase));
        }
    }

    [Fact]
    public void EveryMoment()
    {
        foreach (var testCase in Cases("whyMoment"))
        {
            var actual = WhyReminder.Moment(Frequency(testCase), Day(testCase, "day")!.Value, Quiet(testCase));
            Assert.True(Moment(testCase.GetProperty("expect")) == actual, Name(testCase));
        }
    }

    [Fact]
    public void EveryLifeGoalAPeriodShows()
    {
        foreach (var testCase in Cases("whyGoal"))
        {
            var expect = testCase.GetProperty("expect");
            var expected = expect.ValueKind == JsonValueKind.Null ? null : expect.GetString();
            var actual = WhyReminder.Goal(Frequency(testCase), Day(testCase, "periodStart")!.Value, Goals(testCase));
            Assert.True(expected == actual?.Id, Name(testCase));
        }
    }

    [Fact]
    public void EveryLook()
    {
        foreach (var testCase in Cases("whyDue"))
        {
            var actual = WhyReminder.Due(Frequency(testCase), Quiet(testCase), Time(testCase, "since")!.Value, Time(testCase, "now")!.Value);
            Assert.True(Moment(testCase.GetProperty("expect")) == actual, Name(testCase));
        }
    }

    [Fact]
    public void EveryNextMoment()
    {
        foreach (var testCase in Cases("whyNext"))
        {
            var actual = WhyReminder.Next(Frequency(testCase), Quiet(testCase), Time(testCase, "now")!.Value);
            Assert.True(Time(testCase, "expect") == actual, Name(testCase));
        }
    }

    private static string Name(JsonElement testCase) => testCase.GetProperty("name").GetString()!;

    private static WhyFrequency Frequency(JsonElement testCase) => WhyFrequencies.Of(testCase.GetProperty("frequency").GetString());

    private static DateOnly? Day(JsonElement testCase, string name) =>
        testCase.GetProperty(name) is { ValueKind: JsonValueKind.String } value
            ? DateOnly.ParseExact(value.GetString()!, "yyyy-MM-dd", CultureInfo.InvariantCulture)
            : null;

    private static DateTime? Time(JsonElement testCase, string name) =>
        testCase.GetProperty(name) is { ValueKind: JsonValueKind.String } value
            ? DateTime.Parse(value.GetString()!, CultureInfo.InvariantCulture)
            : null;

    private static QuietHours Quiet(JsonElement testCase)
    {
        if (testCase.GetProperty("quietHours") is not { ValueKind: JsonValueKind.String } value)
        {
            return QuietHours.Off;
        }

        var parts = value.GetString()!.Split('-');
        return new QuietHours(TimeOnly.Parse(parts[0], CultureInfo.InvariantCulture), TimeOnly.Parse(parts[1], CultureInfo.InvariantCulture));
    }

    private static WhyMoment? Moment(JsonElement value) => value.ValueKind == JsonValueKind.Null
        ? null
        : new WhyMoment(Day(value, "periodStart")!.Value, Time(value, "at")!.Value);

    private static List<LifeGoalItem> Goals(JsonElement testCase) =>
        [.. testCase.GetProperty("lifeGoals").EnumerateArray().Select(goal => new LifeGoalItem(
            goal.GetProperty("id").GetString()!,
            goal.GetProperty("id").GetString()!,
            "Because",
            Status: goal.GetProperty("status").GetString()!,
            Position: goal.GetProperty("position").GetDouble(),
            CreatedAt: goal.GetProperty("createdAt").GetString()!,
            ClosedAt: goal.GetProperty("closedAt").ValueKind == JsonValueKind.Null ? null : goal.GetProperty("closedAt").GetString()))];

    private IEnumerable<JsonElement> Cases(string name) => vectors.GetProperty(name).EnumerateArray();
}
