using System.Globalization;
using System.Text.Json;
using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>contracts/vectors/reminders.json, the same file the Android tests read.</summary>
public sealed class ReminderRulesContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/reminders.json").RootElement;

    [Fact]
    public void EveryDueTime()
    {
        var failures = new List<string>();
        var cases = vectors.GetProperty("due").EnumerateArray().ToList();
        foreach (var testCase in cases)
        {
            var expected = DateTimeOrNull(testCase.GetProperty("due"));
            var actual = ReminderRules.Due(Reminder(testCase), Task(testCase.GetProperty("task")));
            if (actual != expected)
            {
                failures.Add($"{Name(testCase)}: expected {expected}, got {actual}");
            }
        }

        Assert.True(failures.Count == 0, $"{failures.Count} of {cases.Count} failed:\n{string.Join('\n', failures)}");
    }

    [Fact]
    public void EveryQuietHoursWindow()
    {
        var failures = new List<string>();
        var cases = vectors.GetProperty("quietHours").EnumerateArray().ToList();
        foreach (var testCase in cases)
        {
            var window = new QuietHours(Time(testCase.GetProperty("start"))!.Value, Time(testCase.GetProperty("end"))!.Value);
            var actual = window.Release(DateTimeOrNull(testCase.GetProperty("at"))!.Value, testCase.GetProperty("important").GetBoolean());
            var expected = DateTimeOrNull(testCase.GetProperty("fires"));
            if (actual != expected)
            {
                failures.Add($"{Name(testCase)}: expected {expected}, got {actual}");
            }
        }

        Assert.True(failures.Count == 0, $"{failures.Count} of {cases.Count} failed:\n{string.Join('\n', failures)}");
    }

    [Fact]
    public void EverySnooze()
    {
        var failures = new List<string>();
        var cases = vectors.GetProperty("snooze").EnumerateArray().ToList();
        foreach (var testCase in cases)
        {
            var option = testCase.GetProperty("option").GetString() switch
            {
                "tenMinutes" => Snooze.TenMinutes,
                "oneHour" => Snooze.OneHour,
                "tomorrowMorning" => Snooze.TomorrowMorning,
                var other => throw new InvalidOperationException($"Unknown snooze option: {other}"),
            };
            var actual = option.Target(DateTimeOrNull(testCase.GetProperty("now"))!.Value, testCase.GetProperty("dayStartHour").GetInt32());
            var expected = DateTimeOrNull(testCase.GetProperty("at"));
            if (actual != expected)
            {
                failures.Add($"{Name(testCase)}: expected {expected}, got {actual}");
            }
        }

        Assert.True(failures.Count == 0, $"{failures.Count} of {cases.Count} failed:\n{string.Join('\n', failures)}");
    }

    [Fact]
    public void TheMorningHourMatchesTheVectors()
    {
        Assert.Equal(SnoozeTimes.MorningHour, vectors.GetProperty("morningHour").GetInt32());
    }

    private static ReminderItem Reminder(JsonElement testCase) => new(
        "11111111-2222-4333-8444-555555555555",
        "66666666-7777-4888-8999-aaaaaaaaaaaa",
        testCase.GetProperty("state").GetString() switch
        {
            "snoozed" => ReminderState.Snoozed,
            "dismissed" => ReminderState.Dismissed,
            "done" => ReminderState.Done,
            _ => ReminderState.Pending,
        },
        Important: testCase.TryGetProperty("important", out var important) && important.GetBoolean(),
        FireAt: DateTimeOrNull(testCase.GetProperty("fireAt")),
        OffsetMinutes: testCase.GetProperty("offsetMinutes") is { ValueKind: not JsonValueKind.Null } offset ? offset.GetInt32() : null,
        SnoozedUntil: DateTimeOrNull(testCase.GetProperty("snoozedUntil")),
        Deleted: testCase.GetProperty("reminderDeleted").GetBoolean());

    private static TaskItem Task(JsonElement task) => new(
        "66666666-7777-4888-8999-aaaaaaaaaaaa",
        "Take the bread out",
        task.GetProperty("status").GetString() switch
        {
            "done" => TaskState.Done,
            "dropped" => TaskState.Dropped,
            _ => TaskState.Open,
        },
        false,
        "2026-09-10T08:00:00.000000Z",
        Date(task.GetProperty("planned")),
        Time(task.GetProperty("time")),
        Deleted: task.GetProperty("deleted").GetBoolean());

    private static string? Name(JsonElement testCase) => testCase.GetProperty("name").GetString();

    private static DateOnly? Date(JsonElement value) =>
        value.ValueKind == JsonValueKind.Null ? null : DateOnly.ParseExact(value.GetString()!, "yyyy-MM-dd", CultureInfo.InvariantCulture);

    private static TimeOnly? Time(JsonElement value) =>
        value.ValueKind == JsonValueKind.Null ? null : TimeOnly.ParseExact(value.GetString()!, "HH:mm", CultureInfo.InvariantCulture);

    private static DateTime? DateTimeOrNull(JsonElement value) =>
        value.ValueKind == JsonValueKind.Null ? null : DateTime.ParseExact(value.GetString()!, "yyyy-MM-dd'T'HH:mm", CultureInfo.InvariantCulture);
}
