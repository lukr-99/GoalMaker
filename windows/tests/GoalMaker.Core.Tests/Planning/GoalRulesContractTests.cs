using System.Globalization;
using System.Text.Json;
using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>contracts/vectors/goals.json, the same file the Android tests and the connector read.</summary>
public sealed class GoalRulesContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/goals.json").RootElement;

    [Fact]
    public void EveryPeriod()
    {
        foreach (var testCase in vectors.GetProperty("periods").EnumerateArray())
        {
            var horizon = Horizon(testCase);
            var start = GoalRules.PeriodStart(horizon, Day(testCase, "day"));
            Assert.Equal(Day(testCase, "start"), start);
            Assert.Equal(Day(testCase, "end"), GoalRules.PeriodEnd(horizon, start));
        }
    }

    [Fact]
    public void EveryParent()
    {
        foreach (var testCase in vectors.GetProperty("parents").EnumerateArray())
        {
            var child = testCase.GetProperty("child");
            var parent = testCase.GetProperty("parent");
            Assert.True(
                testCase.GetProperty("allowed").GetBoolean() == GoalRules.CanServe(Horizon(child), Day(child, "start"), Horizon(parent), Day(parent, "start")),
                testCase.GetProperty("name").GetString());
        }
    }

    [Fact]
    public void EveryProgress()
    {
        foreach (var testCase in vectors.GetProperty("progress").EnumerateArray())
        {
            var goal = testCase.GetProperty("goal");
            var tasks = testCase.GetProperty("tasks").EnumerateArray().Select((task, index) => new TaskItem(
                $"t{index}",
                "Task",
                task.GetProperty("status").GetString() switch
                {
                    "done" => TaskState.Done,
                    "dropped" => TaskState.Dropped,
                    _ => TaskState.Open,
                },
                false,
                "2026-09-10T08:00:00.000000Z")
            {
                Deleted = task.TryGetProperty("deleted", out var deleted) && deleted.GetBoolean(),
            });
            var entries = testCase.GetProperty("entries").EnumerateArray().Select((entry, index) => new GoalEntryItem(
                $"e{index}",
                "g",
                new DateOnly(2026, 9, 18),
                entry.GetProperty("amount").GetDouble(),
                entry.TryGetProperty("deleted", out var deleted) && deleted.GetBoolean()));
            var expect = testCase.GetProperty("expect");
            var progress = GoalRules.Progress(
                goal.GetProperty("mode").GetString()!,
                goal.GetProperty("status").GetString()!,
                goal.TryGetProperty("target", out var target) ? target.GetDouble() : null,
                tasks,
                entries);
            var name = testCase.GetProperty("name").GetString();
            Assert.True(Math.Abs(expect.GetProperty("value").GetDouble() - progress.Value) < 1e-9, $"{name}: value {progress.Value}");
            Assert.True(Math.Abs(expect.GetProperty("target").GetDouble() - progress.Target) < 1e-9, $"{name}: target {progress.Target}");
            Assert.True(Math.Abs(expect.GetProperty("fraction").GetDouble() - progress.Fraction) < 1e-9, $"{name}: fraction {progress.Fraction}");
            Assert.True(expect.GetProperty("hit").GetBoolean() == progress.Hit, $"{name}: hit {progress.Hit}");
        }
    }

    [Fact]
    public void EveryCopy()
    {
        foreach (var testCase in vectors.GetProperty("copy").EnumerateArray())
        {
            var to = testCase.GetProperty("to");
            var goals = testCase.GetProperty("goals").EnumerateArray().Select(goal => new GoalItem(
                goal.GetProperty("id").GetString()!,
                goal.GetProperty("title").GetString()!,
                Horizon(to),
                Day(to, "start").AddDays(-7))
            {
                Status = goal.GetProperty("status").GetString()!,
                ParentId = goal.GetProperty("parent").GetString(),
            });
            var parents = testCase.GetProperty("parents").EnumerateArray().ToDictionary(
                parent => parent.GetProperty("id").GetString()!,
                parent => new GoalItem(parent.GetProperty("id").GetString()!, "Parent", Horizon(parent), Day(parent, "start")));
            var expected = testCase.GetProperty("expect").EnumerateArray().Select(copy => (copy.GetProperty("title").GetString(), copy.GetProperty("parent").GetString()));
            var actual = GoalRules.Copies(goals, parents, Horizon(to), Day(to, "start")).Select(copy => ((string?)copy.Title, copy.ParentId));
            Assert.Equal(expected, actual);
        }
    }

    private static GoalHorizon Horizon(JsonElement element) => GoalRules.HorizonOf(element.GetProperty("horizon").GetString())!.Value;

    private static DateOnly Day(JsonElement element, string name) =>
        DateOnly.ParseExact(element.GetProperty(name).GetString()!, "yyyy-MM-dd", CultureInfo.InvariantCulture);
}
