using System.Globalization;
using System.Text.Json;
using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>The habit reminder cases of contracts/vectors/reminders.json, the same file the Android tests read.</summary>
public sealed class HabitReminderContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/reminders.json").RootElement;

    [Fact]
    public void EveryHabitRemindersDayAndNextMoment()
    {
        foreach (var testCase in vectors.GetProperty("habitReminders").EnumerateArray())
        {
            var name = testCase.GetProperty("name").GetString();
            var hour = testCase.GetProperty("dayStartHour").GetInt32();
            var now = Moment(testCase, "now")!.Value;
            Assert.True(
                OptionalDay(testCase, "due") == HabitReminder.Due(Habit(testCase), Checkins(testCase), Pauses(testCase), hour, Moment(testCase, "since")!.Value, now),
                $"{name}: due");
            Assert.True(
                Moment(testCase, "next") == HabitReminder.Next(Habit(testCase), Checkins(testCase), Pauses(testCase), hour, now),
                $"{name}: next");
        }
    }

    [Fact]
    public void EveryStaleHabitReminder()
    {
        foreach (var testCase in vectors.GetProperty("habitReminderStale").EnumerateArray())
        {
            Assert.True(
                testCase.GetProperty("expect").GetBoolean() == HabitReminder.Stale(
                    Habit(testCase),
                    OptionalDay(testCase, "day")!.Value,
                    Checkins(testCase),
                    Pauses(testCase),
                    testCase.GetProperty("dayStartHour").GetInt32(),
                    Moment(testCase, "now")!.Value),
                testCase.GetProperty("name").GetString());
        }
    }

    private static HabitItem Habit(JsonElement testCase)
    {
        var habit = testCase.GetProperty("habit");
        return new HabitItem("h", "Habit", OptionalDay(habit, "startsOn")!.Value)
        {
            Cadence = habit.GetProperty("cadence").GetString()!,
            Weekdays = habit.GetProperty("weekdays").ValueKind == JsonValueKind.Null ? null : habit.GetProperty("weekdays").GetInt32(),
            Times = habit.GetProperty("times").ValueKind == JsonValueKind.Null ? null : habit.GetProperty("times").GetInt32(),
            Measure = habit.GetProperty("measure").GetString()!,
            Target = habit.GetProperty("target").ValueKind == JsonValueKind.Null ? null : habit.GetProperty("target").GetDouble(),
            Direction = habit.TryGetProperty("direction", out var direction) ? direction.GetString()! : HabitRules.AtLeast,
            RemindAt = habit.GetProperty("remindAt").GetString() is { } time ? TimeOnly.ParseExact(time, "HH:mm", CultureInfo.InvariantCulture) : null,
        };
    }

    private static List<HabitCheckin> Checkins(JsonElement testCase) =>
        testCase.GetProperty("checkins").EnumerateArray().Select((checkin, index) => new HabitCheckin(
            $"c{index}",
            "h",
            OptionalDay(checkin, "day")!.Value,
            checkin.GetProperty("value").GetDouble(),
            checkin.GetProperty("skipped").GetBoolean(),
            Failed: checkin.TryGetProperty("failed", out var failed) && failed.GetBoolean())).ToList();

    private static List<HabitPause> Pauses(JsonElement testCase) =>
        testCase.GetProperty("pauses").EnumerateArray().Select((pause, index) => new HabitPause(
            $"p{index}",
            "h",
            OptionalDay(pause, "from")!.Value,
            OptionalDay(pause, "until"))).ToList();

    private static DateOnly? OptionalDay(JsonElement element, string name) =>
        element.GetProperty(name).GetString() is { } text ? DateOnly.ParseExact(text, "yyyy-MM-dd", CultureInfo.InvariantCulture) : null;

    private static DateTime? Moment(JsonElement element, string name) =>
        element.GetProperty(name).GetString() is { } text ? DateTime.ParseExact(text, "yyyy-MM-dd'T'HH:mm", CultureInfo.InvariantCulture) : null;
}
