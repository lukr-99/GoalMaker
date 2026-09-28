using System.Globalization;
using System.Text.Json;
using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>contracts/vectors/wants.json, the same file the Android tests and the connector read.</summary>
public sealed class WantRulesContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/wants.json").RootElement;

    [Fact]
    public void TheDefaultsAreTheOnesTheContractNames() =>
        Assert.Equal(Cooldowns(vectors.GetProperty("defaults")), WantCooldowns.Default);

    [Fact]
    public void EveryCooldown()
    {
        foreach (var testCase in vectors.GetProperty("cooldown").EnumerateArray())
        {
            var thresholds = testCase.TryGetProperty("cooldowns", out var own) ? Cooldowns(own) : WantCooldowns.Default;
            int? picked = testCase.TryGetProperty("picked", out var days) ? days.GetInt32() : null;
            var actual = WantRules.CooldownDays(Number(testCase, "price"), testCase.GetProperty("currency").GetString()!, thresholds, picked);
            Assert.True(testCase.GetProperty("expect").GetInt32() == actual, Name(testCase));
        }
    }

    [Fact]
    public void EveryDayItCools()
    {
        foreach (var testCase in vectors.GetProperty("coolsUntil").EnumerateArray())
        {
            var actual = WantRules.CoolsUntil(Day(testCase, "addedOn")!.Value, testCase.GetProperty("days").GetInt32());
            Assert.True(Day(testCase, "expect") == actual, Name(testCase));
        }
    }

    [Fact]
    public void EveryState()
    {
        foreach (var testCase in vectors.GetProperty("state").EnumerateArray())
        {
            var now = DateTime.Parse(testCase.GetProperty("now").GetString()!, CultureInfo.InvariantCulture);
            var today = PlanningDay.Of(now, testCase.GetProperty("dayStartHour").GetInt32());
            var state = WantRules.State(Want(testCase.GetProperty("want")), today);
            var expect = testCase.GetProperty("expect");
            var expected = expect.ValueKind == JsonValueKind.Null ? null : expect.GetString();
            Assert.True(expected == state?.ToString().ToLowerInvariant(), Name(testCase));
        }
    }

    [Fact]
    public void EveryRing()
    {
        foreach (var testCase in vectors.GetProperty("progress").EnumerateArray())
        {
            var actual = WantRules.Progress(Want(testCase.GetProperty("want")), Day(testCase, "today")!.Value);
            Assert.True(Math.Abs(testCase.GetProperty("expect").GetDouble() - actual) < 1e-9, Name(testCase));
        }
    }

    [Fact]
    public void EveryNotification()
    {
        foreach (var testCase in vectors.GetProperty("ready").EnumerateArray())
        {
            var wants = testCase.GetProperty("wants").EnumerateArray().Select(Want).ToList();
            var actual = WantRules.Ready(wants, Day(testCase, "lastNotified"), Day(testCase, "today")!.Value).Select(want => want.Id);
            var expected = testCase.GetProperty("expect").EnumerateArray().Select(id => id.GetString()!);
            Assert.True(expected.SequenceEqual(actual), Name(testCase));
        }
    }

    [Fact]
    public void EveryStatsBlock()
    {
        foreach (var testCase in vectors.GetProperty("stats").EnumerateArray())
        {
            var expect = testCase.GetProperty("expect");
            var stats = WantRules.Stats(
                testCase.GetProperty("wants").EnumerateArray().Select(Want),
                testCase.GetProperty("currency").GetString()!);
            Assert.True(expect.GetProperty("bought").GetInt32() == stats.Bought, Name(testCase));
            Assert.True(expect.GetProperty("dropped").GetInt32() == stats.Dropped, Name(testCase));
            Assert.True(Math.Abs(expect.GetProperty("notSpent").GetDouble() - stats.NotSpent) < 1e-9, Name(testCase));
        }
    }

    [Fact]
    public void EveryThresholdsId()
    {
        Assert.Equal("b8c61b22-5e0c-4f0a-9d1e-6f4a2c7e3b91", vectors.GetProperty("namespace").GetString());
        foreach (var testCase in vectors.GetProperty("cooldownsId").EnumerateArray())
        {
            var actual = WantRules.CooldownsId(testCase.GetProperty("owner").GetString()!);
            Assert.True(testCase.GetProperty("expect").GetString() == actual, Name(testCase));
        }
    }

    private static WantCooldowns Cooldowns(JsonElement value) => new(
        value.GetProperty("smallUnder").GetDouble(),
        value.GetProperty("smallDays").GetInt32(),
        value.GetProperty("mediumUnder").GetDouble(),
        value.GetProperty("mediumDays").GetInt32(),
        value.GetProperty("largeDays").GetInt32(),
        value.GetProperty("unpricedDays").GetInt32(),
        value.GetProperty("currency").GetString()!);

    private static WantItem Want(JsonElement value)
    {
        var coolsUntil = Day(value, "coolsUntil") ?? new DateOnly(2026, 9, 28);
        var addedOn = Day(value, "addedOn") ?? coolsUntil;
        return new WantItem(
            Text(value, "id") ?? "w",
            Text(value, "title") ?? "Want",
            "Because",
            coolsUntil.DayNumber - addedOn.DayNumber,
            addedOn,
            coolsUntil,
            Price: Number(value, "price"),
            Currency: Text(value, "currency") ?? "CZK",
            Decision: Text(value, "decision"),
            Deleted: value.TryGetProperty("deleted", out var deleted) && deleted.GetBoolean());
    }

    private static string? Text(JsonElement value, string name) =>
        value.TryGetProperty(name, out var text) && text.ValueKind == JsonValueKind.String ? text.GetString() : null;

    private static double? Number(JsonElement value, string name) =>
        value.TryGetProperty(name, out var number) && number.ValueKind == JsonValueKind.Number ? number.GetDouble() : null;

    private static DateOnly? Day(JsonElement value, string name) =>
        Text(value, name) is { } text ? DateOnly.ParseExact(text, "yyyy-MM-dd", CultureInfo.InvariantCulture) : null;

    private static string Name(JsonElement testCase) => testCase.GetProperty("name").GetString()!;
}
