using System.Globalization;
using System.Text.Json;
using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>contracts/vectors/recurrence.json, the same file the Android tests read.</summary>
public sealed class RecurrenceContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/recurrence.json").RootElement;

    [Fact]
    public void EveryNextDay()
    {
        var failures = new List<string>();
        var cases = vectors.GetProperty("next").EnumerateArray().ToList();
        foreach (var testCase in cases)
        {
            var planned = Date(testCase.GetProperty("planned"));
            var expected = Date(testCase.GetProperty("next"));
            var actual = Recurrence.Parse(testCase.GetProperty("rule").GetString())?.Next(planned, Date(testCase.GetProperty("today"))!.Value);
            if (actual != expected)
            {
                failures.Add($"{testCase.GetProperty("name").GetString()}: expected {expected}, got {actual}");
            }
        }

        Assert.True(failures.Count == 0, $"{failures.Count} of {cases.Count} failed:\n{string.Join('\n', failures)}");
    }

    [Fact]
    public void SuccessorAndTagLinkIds()
    {
        foreach (var pair in vectors.GetProperty("successors").EnumerateArray())
        {
            Assert.Equal(pair.GetProperty("successor").GetString(), Occurrences.SuccessorId(pair.GetProperty("id").GetString()!));
        }

        foreach (var link in vectors.GetProperty("tagLinks").EnumerateArray())
        {
            Assert.Equal(link.GetProperty("id").GetString(), Occurrences.TagLinkId(link.GetProperty("task").GetString()!, link.GetProperty("tag").GetString()!));
        }
    }

    [Fact]
    public void EveryRepair()
    {
        foreach (var testCase in vectors.GetProperty("repair").EnumerateArray())
        {
            var tasks = testCase.GetProperty("occurrences").EnumerateArray().Select(occurrence => new TaskItem(
                occurrence.GetProperty("id").GetString()!,
                "Run",
                occurrence.GetProperty("status").GetString() switch
                {
                    "done" => TaskState.Done,
                    "dropped" => TaskState.Dropped,
                    _ => TaskState.Open,
                },
                false,
                "2026-09-10T08:00:00.000000Z",
                Date(occurrence.GetProperty("planned")),
                Recurrence: "FREQ=DAILY",
                Deleted: occurrence.TryGetProperty("deleted", out var deleted) && deleted.GetBoolean(),
                SeriesId: occurrence.GetProperty("series").GetString()));
            var expected = testCase.GetProperty("drop").EnumerateArray().Select(id => id.GetString()!).Order(StringComparer.Ordinal);
            Assert.True(
                expected.SequenceEqual(Occurrences.ToDrop(tasks).Order(StringComparer.Ordinal)),
                testCase.GetProperty("name").GetString());
        }
    }

    private static DateOnly? Date(JsonElement value) =>
        value.ValueKind == JsonValueKind.Null ? null : DateOnly.ParseExact(value.GetString()!, "yyyy-MM-dd", CultureInfo.InvariantCulture);
}
