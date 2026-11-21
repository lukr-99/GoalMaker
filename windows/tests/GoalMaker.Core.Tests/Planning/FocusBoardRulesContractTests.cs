using System.Text.Json;
using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>contracts/vectors/focus.json, the same file the Android tests and the connector read.</summary>
public sealed class FocusBoardRulesContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/focus.json").RootElement;

    [Fact]
    public void TheBoardsOrder()
    {
        foreach (var testCase in vectors.GetProperty("board").EnumerateArray())
        {
            Assert.True(
                testCase.GetProperty("expect").EnumerateArray().Select(id => id.GetString()!)
                    .SequenceEqual(FocusBoardRules.Board(Tasks(testCase)).Select(task => task.Id)),
                Name(testCase));
        }
    }

    [Fact]
    public void AddingAtTheEnd()
    {
        foreach (var testCase in vectors.GetProperty("append").EnumerateArray())
        {
            Assert.True(
                testCase.GetProperty("expect").GetDouble() == FocusBoardRules.AppendPosition(Tasks(testCase)),
                Name(testCase));
        }
    }

    [Fact]
    public void PuttingATaskAtAPlace()
    {
        foreach (var testCase in vectors.GetProperty("placeAt").EnumerateArray())
        {
            var position = FocusBoardRules.PlaceAt(
                Tasks(testCase),
                testCase.GetProperty("task").GetString()!,
                testCase.GetProperty("place").GetInt32());
            Assert.True(testCase.GetProperty("expect").GetDouble() == position, Name(testCase));
        }
    }

    [Fact]
    public void WhatTheAddPickerOffers()
    {
        foreach (var testCase in vectors.GetProperty("candidates").EnumerateArray())
        {
            Assert.True(
                testCase.GetProperty("expect").EnumerateArray().Select(id => id.GetString()!)
                    .SequenceEqual(FocusBoardRules.Candidates(Tasks(testCase), testCase.GetProperty("query").GetString()!).Select(task => task.Id)),
                Name(testCase));
        }
    }

    private static string? Optional(JsonElement task, string name) =>
        task.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String ? value.GetString() : null;

    private static string Name(JsonElement testCase) => testCase.GetProperty("name").GetString()!;

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
            PlannedDate: Optional(task, "day") is { } day ? DateOnly.Parse(day, System.Globalization.CultureInfo.InvariantCulture) : null,
            Notes: Optional(task, "notes") ?? string.Empty,
            Deadline: Optional(task, "deadline") is { } deadline ? DateOnly.Parse(deadline, System.Globalization.CultureInfo.InvariantCulture) : null,
            Deleted: task.TryGetProperty("deleted", out var deleted) && deleted.GetBoolean(),
            FocusPosition: task.GetProperty("focus").ValueKind == JsonValueKind.Null
                ? null
                : task.GetProperty("focus").GetDouble())).ToList();
}
