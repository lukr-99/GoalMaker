using System.Globalization;
using System.Text.Json;
using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>contracts/vectors/lists.json, the same file the Android tests read.</summary>
public sealed class ListRulesContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/lists.json").RootElement;

    [Fact]
    public void EveryFilter()
    {
        foreach (var testCase in vectors.GetProperty("filter").EnumerateArray())
        {
            var listed = testCase.GetProperty("tasks").EnumerateArray().ToList();
            var tasks = listed.Select(task => new TaskItem(
                task.GetProperty("id").GetString()!,
                "Task",
                TaskState.Open,
                false,
                "2026-09-10T08:00:00.000000Z",
                AreaId: task.TryGetProperty("area", out var area) ? area.GetString() : null)).ToList();
            var links = listed.ToDictionary(
                task => task.GetProperty("id").GetString()!,
                task => (IReadOnlySet<string>)(task.TryGetProperty("tags", out var tags) ? tags.EnumerateArray().Select(tag => tag.GetString()!).ToHashSet() : []));
            var filter = new ListFilter(testCase.GetProperty("area").GetString(), testCase.GetProperty("tag").GetString());
            var expected = testCase.GetProperty("keep").EnumerateArray().Select(id => id.GetString()!);

            Assert.True(expected.SequenceEqual(filter.Apply(tasks, links).Select(task => task.Id)), testCase.GetProperty("name").GetString());
        }
    }

    [Fact]
    public void EveryListVector()
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
            var tasks = testCase.GetProperty("tasks").EnumerateArray().Select((task, index) => Task(task, defaults, index)).ToList();
            var lists = ListRules.Lists(tasks, PlanningDay.Of(now, startHour));
            var expect = testCase.GetProperty("expect");
            string Ids(string name) => expect.TryGetProperty(name, out var ids) ? string.Join(",", ids.EnumerateArray().Select(id => id.GetString())) : string.Empty;
            var summary = expect.TryGetProperty("summary", out var s) ? $"{s.GetProperty("done").GetInt32()} of {s.GetProperty("total").GetInt32()}" : "0 of 0";
            string[] expected = [Ids("priorities"), Ids("scheduled"), Ids("more"), Ids("overdue"), Ids("tomorrow"), Ids("inbox"), summary];
            var sections = lists.TodaySections;
            string[] actual =
            [
                .. new[] { sections.Priorities, sections.Scheduled, sections.More, sections.Overdue, lists.Tomorrow, lists.Inbox }
                    .Select(list => string.Join(",", list.Select(task => task.Id))),
                $"{lists.Summary.Done} of {lists.Summary.Total}",
            ];
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
            Field("deleted").GetBoolean(),
            ProjectId: Text("project"));
    }
}
