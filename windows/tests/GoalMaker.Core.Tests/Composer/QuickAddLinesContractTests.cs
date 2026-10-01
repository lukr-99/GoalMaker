using System.Globalization;
using System.Text.Json.Nodes;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Tests.Composer;

/// <summary>contracts/vectors/quick-add.json, the same file the Android tests and the connector's rules read.</summary>
public sealed class QuickAddLinesContractTests
{
    private readonly JsonObject vectors = JsonNode.Parse(ContractFiles.Load("vectors/quick-add.json").RootElement.GetRawText())!.AsObject();

    [Fact]
    public void EveryWantLine() => Run("wants", line =>
    {
        var want = QuickAddLines.ReadWant(line);
        return new JsonObject
        {
            ["title"] = want.Title,
            ["reason"] = want.Reason,
            ["price"] = want.Price,
            ["currency"] = want.Currency,
            ["waitDays"] = want.WaitDays,
        };
    });

    [Fact]
    public void EveryHabitLine() => Run("habits", line =>
    {
        var habit = QuickAddLines.ReadHabit(line);
        return new JsonObject
        {
            ["name"] = habit.Name,
            ["cadence"] = habit.Cadence,
            ["weekdays"] = habit.Weekdays,
            ["times"] = habit.Times,
            ["measure"] = habit.Measure,
            ["target"] = habit.Target,
            ["unit"] = habit.Unit,
        };
    });

    [Fact]
    public void EveryGoalLine()
    {
        var today = DateOnly.ParseExact((string)vectors["today"]!, "yyyy-MM-dd", CultureInfo.InvariantCulture);
        Run("goals", line =>
        {
            var goal = QuickAddLines.ReadGoal(line, today);
            return new JsonObject
            {
                ["title"] = goal.Title,
                ["horizon"] = GoalRules.Id(goal.Horizon),
                ["periodStart"] = goal.PeriodStart.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture),
                ["mode"] = goal.Mode,
                ["target"] = goal.Target,
                ["unit"] = goal.Unit,
            };
        });
    }

    private void Run(string group, Func<string, JsonObject> read)
    {
        var defaults = vectors[group]!["defaults"]!.AsObject();
        var cases = vectors[group]!["cases"]!.AsArray().Select(node => node!.AsObject()).ToList();
        var failures = new List<string>();
        foreach (var testCase in cases)
        {
            var expected = (JsonObject)defaults.DeepClone();
            foreach (var (key, value) in testCase["expect"]!.AsObject())
            {
                expected[key] = value?.DeepClone();
            }

            var actual = read((string)testCase["line"]!);
            // Numbers compare by value, so 3290 and 3290.0 agree.
            if (!JsonNode.DeepEquals(Normalize(actual), Normalize(expected)))
            {
                failures.Add($"{testCase["name"]}: expected {expected.ToJsonString()}\n    got      {actual.ToJsonString()}");
            }
        }

        Assert.True(failures.Count == 0, $"{failures.Count} of {cases.Count} failed:\n{string.Join('\n', failures)}");
    }

    private static JsonObject Normalize(JsonObject value)
    {
        var copy = new JsonObject();
        foreach (var (key, node) in value)
        {
            copy[key] = node is JsonValue number && number.TryGetValue<double>(out var d) ? JsonValue.Create(d) : node?.DeepClone();
        }

        return copy;
    }
}
