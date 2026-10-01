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

    [Fact]
    public void EveryPace()
    {
        foreach (var testCase in vectors.GetProperty("pace").EnumerateArray())
        {
            var goal = testCase.GetProperty("goal");
            var progress = testCase.GetProperty("progress");
            var item = new GoalItem("g", "Goal", Horizon(goal), Day(goal, "start"))
            {
                Mode = goal.GetProperty("mode").GetString()!,
                Status = goal.GetProperty("status").GetString()!,
            };
            var standing = GoalRules.Standing(
                item,
                new GoalProgress(
                    progress.GetProperty("value").GetDouble(),
                    progress.GetProperty("target").GetDouble(),
                    progress.GetProperty("fraction").GetDouble(),
                    progress.GetProperty("hit").GetBoolean()),
                Day(testCase, "today"));
            var expect = testCase.GetProperty("expect");
            var name = testCase.GetProperty("name").GetString();
            Assert.True(expect.GetProperty("pace").GetString() == GoalRules.PaceId(standing.Pace), $"{name}: {standing.Pace}");
            var behind = expect.GetProperty("behind");
            Assert.True(
                (behind.ValueKind == JsonValueKind.Null ? (double?)null : behind.GetDouble()) == standing.Behind,
                $"{name}: behind {standing.Behind}");
        }
    }

    [Fact]
    public void EveryOrder()
    {
        foreach (var testCase in vectors.GetProperty("order").EnumerateArray())
        {
            var goals = testCase.GetProperty("goals").EnumerateArray()
                .Select(goal => (Id: goal.GetProperty("id").GetString()!, Pace: Pace(goal.GetProperty("pace").GetString())))
                .ToList();
            var expected = testCase.GetProperty("expect").EnumerateArray().Select(id => id.GetString()!);
            Assert.Equal(expected, GoalRules.ByPace(goals, goal => goal.Pace).Select(goal => goal.Id));
        }
    }

    [Fact]
    public void EveryChain()
    {
        foreach (var testCase in vectors.GetProperty("chain").EnumerateArray())
        {
            var goals = testCase.GetProperty("goals").EnumerateArray().Select(goal =>
                new GoalItem(goal.GetProperty("id").GetString()!, "Goal", GoalHorizon.Week, new DateOnly(2026, 9, 28))
                {
                    ParentId = goal.GetProperty("parent").GetString(),
                });
            var expected = testCase.GetProperty("expect").EnumerateArray().Select(id => id.GetString()!);
            Assert.Equal(expected, GoalRules.Chain(goals, testCase.GetProperty("picked").GetString()!).Order(StringComparer.Ordinal));
        }
    }

    [Fact]
    public void EveryQuickAmount()
    {
        foreach (var testCase in vectors.GetProperty("quick").EnumerateArray())
        {
            var entries = testCase.GetProperty("entries").EnumerateArray().Select((entry, index) => new GoalEntryItem(
                $"e{index}",
                "g",
                new DateOnly(2026, 9, 30),
                entry.GetProperty("amount").GetDouble(),
                entry.TryGetProperty("deleted", out var deleted) && deleted.GetBoolean(),
                entry.GetProperty("created").GetString()!));
            var expect = testCase.GetProperty("expect");
            Assert.True(
                (expect.ValueKind == JsonValueKind.Null ? (double?)null : expect.GetDouble()) == GoalRules.QuickAmount(entries),
                testCase.GetProperty("name").GetString());
        }
    }

    private static GoalPace Pace(string? id) => id switch
    {
        "behind" => GoalPace.Behind,
        "on_track" => GoalPace.OnTrack,
        "hit" => GoalPace.Hit,
        _ => GoalPace.Dropped,
    };

    private static GoalHorizon Horizon(JsonElement element) => GoalRules.HorizonOf(element.GetProperty("horizon").GetString())!.Value;

    private static DateOnly Day(JsonElement element, string name) =>
        DateOnly.ParseExact(element.GetProperty(name).GetString()!, "yyyy-MM-dd", CultureInfo.InvariantCulture);
}
