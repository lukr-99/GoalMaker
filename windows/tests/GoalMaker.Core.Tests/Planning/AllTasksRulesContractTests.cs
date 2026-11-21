using System.Globalization;
using System.Text.Json;
using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>contracts/vectors/all-tasks.json, the same file the Android tests and the connector read.</summary>
public sealed class AllTasksRulesContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/all-tasks.json").RootElement;

    [Fact]
    public void TheGroups()
    {
        foreach (var testCase in vectors.GetProperty("groups").EnumerateArray())
        {
            var groups = AllTasksRules.Groups(
                Tasks(testCase),
                [.. testCase.GetProperty("areas").EnumerateArray().Select(area => area.GetString()!)],
                testCase.GetProperty("projects").EnumerateObject().ToDictionary(
                    project => project.Name,
                    project => project.Value.ValueKind == JsonValueKind.String ? project.Value.GetString() : null,
                    StringComparer.Ordinal),
                testCase.GetProperty("query").GetString()!,
                testCase.GetProperty("projectItems").GetBoolean());
            var expect = testCase.GetProperty("expect").EnumerateArray()
                .Select(group => $"{Optional(group, "area") ?? "-"}: {string.Join(",", group.GetProperty("tasks").EnumerateArray().Select(id => id.GetString()))}");
            var actual = groups.Select(group => $"{group.AreaId ?? "-"}: {string.Join(",", group.Tasks.Select(task => task.Id))}");
            Assert.True(expect.SequenceEqual(actual), testCase.GetProperty("name").GetString());
        }
    }

    private static string? Optional(JsonElement element, string name) =>
        element.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String ? value.GetString() : null;

    private static List<TaskItem> Tasks(JsonElement testCase) =>
        testCase.GetProperty("tasks").EnumerateArray().Select(task => new TaskItem(
            Id: task.GetProperty("id").GetString()!,
            Title: Optional(task, "title") ?? task.GetProperty("id").GetString()!,
            State: task.GetProperty("state").GetString() switch
            {
                "done" => TaskState.Done,
                "dropped" => TaskState.Dropped,
                _ => TaskState.Open,
            },
            TopPriority: false,
            CreatedAt: task.GetProperty("createdAt").GetString()!,
            PlannedDate: Optional(task, "day") is { } day ? DateOnly.Parse(day, CultureInfo.InvariantCulture) : null,
            AreaId: Optional(task, "area"),
            Deleted: task.TryGetProperty("deleted", out var deleted) && deleted.GetBoolean(),
            Notes: Optional(task, "notes") ?? string.Empty,
            Deadline: Optional(task, "deadline") is { } deadline ? DateOnly.Parse(deadline, CultureInfo.InvariantCulture) : null,
            ProjectId: Optional(task, "project"))).ToList();
}
