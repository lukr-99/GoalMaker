using System.Globalization;
using System.Text.Json;
using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>contracts/vectors/plan.json, the same file the Android tests read.</summary>
public sealed class PlanRulesContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/plan.json").RootElement;

    [Fact]
    public void ThePriorityLimitMatchesTheContract() =>
        Assert.Equal(PlanRules.MaxPriorities, vectors.GetProperty("maxPriorities").GetInt32());

    [Fact]
    public void EveryPlanVector()
    {
        var defaults = vectors.GetProperty("taskDefaults");
        var cases = vectors.GetProperty("cases").EnumerateArray().ToList();
        var failures = new List<string>();
        foreach (var testCase in cases)
        {
            var now = DateTime.ParseExact(
                (testCase.TryGetProperty("now", out var n) ? n : vectors.GetProperty("now")).GetString()!,
                "yyyy-MM-dd'T'HH:mm",
                CultureInfo.InvariantCulture);
            var startHour = (testCase.TryGetProperty("rolloverHour", out var r) ? r : vectors.GetProperty("rolloverHour")).GetInt32();
            var today = PlanningDay.Of(now, startHour);
            var tasks = testCase.GetProperty("tasks").EnumerateArray().Select((task, index) => Task(task, defaults, index)).ToList();
            var expect = testCase.GetProperty("expect");
            var expected = new List<string>
            {
                "review=" + (expect.TryGetProperty("review", out var review) ? string.Join(",", review.EnumerateArray().Select(id => id.GetString())) : string.Empty),
                "priorities=" + (expect.TryGetProperty("priorities", out var priorities) ? priorities.GetInt32() : 0),
                "tomorrow=" + (expect.TryGetProperty("tomorrow", out var tomorrow) ? string.Join(",", tomorrow.EnumerateArray().Select(id => id.GetString())) : string.Empty),
            };
            var actual = new List<string>
            {
                "review=" + string.Join(",", PlanRules.Review(tasks, today).Select(task => task.Id)),
                "priorities=" + PlanRules.Priorities(tasks, today),
                "tomorrow=" + string.Join(",", PlanRules.Tomorrow(tasks, today).Select(task => task.Id)),
            };
            if (expect.TryGetProperty("decisions", out var decisions))
            {
                expected.Add("decisions=" + string.Join(",", decisions.EnumerateObject()
                    .OrderBy(entry => entry.Name, StringComparer.Ordinal)
                    .Select(entry => $"{entry.Name}:{entry.Value.GetString()}")));
                actual.Add("decisions=" + string.Join(",", tasks.Where(task => !task.Deleted)
                    .OrderBy(task => task.Id, StringComparer.Ordinal)
                    .Select(task => $"{task.Id}:{PlanRules.Decision(task, today).ToString().ToLowerInvariant()}")));
            }

            if (!expected.SequenceEqual(actual))
            {
                failures.Add($"{testCase.GetProperty("name").GetString()}: expected [{string.Join(" | ", expected)}], got [{string.Join(" | ", actual)}]");
            }
        }

        Assert.True(failures.Count == 0, $"{failures.Count} of {cases.Count} failed:\n{string.Join('\n', failures)}");
    }

    private static TaskItem Task(JsonElement task, JsonElement defaults, int index)
    {
        JsonElement Field(string name) => task.TryGetProperty(name, out var value) ? value : defaults.GetProperty(name);
        string? Text(string name) => Field(name).ValueKind == JsonValueKind.Null ? null : Field(name).GetString();
        var id = task.GetProperty("id").GetString()!;
        return new TaskItem(
            id,
            id,
            Text("status") switch
            {
                "done" => TaskState.Done,
                "dropped" => TaskState.Dropped,
                _ => TaskState.Open,
            },
            Field("top").GetBoolean(),
            string.Create(CultureInfo.InvariantCulture, $"2026-09-10T08:{index:D2}:00.000000Z"),
            Text("planned") is { } planned ? DateOnly.ParseExact(planned, "yyyy-MM-dd", CultureInfo.InvariantCulture) : null,
            Text("time") is { } time ? TimeOnly.ParseExact(time, "HH:mm", CultureInfo.InvariantCulture) : null,
            Text("area"),
            null,
            Field("deleted").GetBoolean());
    }
}
