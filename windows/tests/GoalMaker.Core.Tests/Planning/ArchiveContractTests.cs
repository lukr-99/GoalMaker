using System.Text.Json;
using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>contracts/vectors/archive.json, the same file the Android tests read.</summary>
public sealed class ArchiveContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/archive.json").RootElement;

    [Fact]
    public void EveryArchiveSearch()
    {
        foreach (var testCase in vectors.GetProperty("cases").EnumerateArray())
        {
            var tasks = testCase.GetProperty("tasks").EnumerateArray().Select(task => new TaskItem(
                task.GetProperty("id").GetString()!,
                task.GetProperty("title").GetString()!,
                task.GetProperty("status").GetString() switch
                {
                    "done" => TaskState.Done,
                    "dropped" => TaskState.Dropped,
                    _ => TaskState.Open,
                },
                false,
                "2026-09-10T08:00:00.000000Z",
                Deleted: task.TryGetProperty("deleted", out var deleted) && deleted.GetBoolean(),
                Notes: task.TryGetProperty("notes", out var notes) ? notes.GetString()! : string.Empty,
                CompletedAt: task.TryGetProperty("completedAt", out var completed) ? completed.GetString() : null)).ToList();
            var expected = testCase.GetProperty("keep").EnumerateArray().Select(id => id.GetString()!);
            var actual = ArchiveRules.Search(tasks, testCase.GetProperty("query").GetString()!).Select(task => task.Id);

            Assert.True(expected.SequenceEqual(actual), $"{testCase.GetProperty("name").GetString()}: got [{string.Join(", ", actual)}]");
        }
    }
}
