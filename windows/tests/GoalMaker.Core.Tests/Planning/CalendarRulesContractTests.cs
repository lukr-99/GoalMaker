using System.Globalization;
using System.Text.Json;
using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>contracts/vectors/calendar.json, the same file the Android tests read.</summary>
public sealed class CalendarRulesContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/calendar.json").RootElement;

    [Fact]
    public void EveryGrid()
    {
        foreach (var testCase in vectors.GetProperty("grids").EnumerateArray())
        {
            var kind = testCase.GetProperty("kind").GetString()!;
            var day = Day(testCase, "day");
            var name = $"{kind} {day}";
            Assert.True(Day(testCase, "start") == CalendarRules.Start(kind, day), name);
            Assert.True(Day(testCase, "end") == CalendarRules.End(kind, day), name);
            var days = CalendarRules.Days(kind, day);
            Assert.True(testCase.GetProperty("count").GetInt32() == days.Count, $"{name}: {days.Count}");
            Assert.Equal(Day(testCase, "start"), days[0]);
            Assert.Equal(Day(testCase, "end"), days[^1]);
        }
    }

    [Fact]
    public void EveryDayOfTheCalendar()
    {
        foreach (var testCase in vectors.GetProperty("days").EnumerateArray())
        {
            var name = testCase.GetProperty("name").GetString();
            var tasks = testCase.GetProperty("tasks").EnumerateArray().Select(task => new TaskItem(
                task.GetProperty("id").GetString()!,
                task.GetProperty("id").GetString()!,
                State(Text(task, "state")),
                false,
                task.GetProperty("createdAt").GetString()!)
            {
                PlannedDate = MaybeDay(task, "plannedDate"),
                PlannedTime = Text(task, "plannedTime") is { } time ? TimeOnly.Parse(time, CultureInfo.InvariantCulture) : null,
                Deadline = MaybeDay(task, "deadline"),
                Recurrence = Text(task, "recurrence"),
                SeriesId = Text(task, "seriesId"),
                Deleted = task.TryGetProperty("deleted", out var deleted) && deleted.ValueKind == JsonValueKind.True,
            }).ToList();
            var reminders = testCase.GetProperty("reminders").EnumerateArray().Select((reminder, index) => new ReminderItem(
                $"r{index}",
                reminder.GetProperty("taskId").GetString()!,
                ReminderState.Pending)
            {
                FireAt = DateTime.Parse(reminder.GetProperty("at").GetString()!, CultureInfo.InvariantCulture),
            }).ToList();

            var days = CalendarRules.Build(tasks, reminders, Day(testCase, "from"), Day(testCase, "to"));
            var expect = testCase.GetProperty("expect");
            Assert.Equal(expect.EnumerateObject().Count(), days.Count);
            foreach (var day in days)
            {
                var row = expect.GetProperty(day.Day.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture));
                Assert.Equal(Ids(row, "planned"), day.Planned.Select(task => task.Id));
                Assert.Equal(Ids(row, "deadlines"), day.Deadlines.Select(task => task.Id));
                Assert.Equal(Ids(row, "repeats"), day.Repeats.Select(task => task.Id));
                Assert.True(row.GetProperty("reminders").GetInt32() == day.Reminders, $"{name} {day.Day}: {day.Reminders}");
            }
        }
    }

    private static IEnumerable<string?> Ids(JsonElement row, string field) =>
        row.GetProperty(field).EnumerateArray().Select(id => id.GetString());

    private static TaskState State(string? name) => name switch
    {
        "done" => TaskState.Done,
        "dropped" => TaskState.Dropped,
        _ => TaskState.Open,
    };

    private static string? Text(JsonElement row, string name) =>
        row.TryGetProperty(name, out var value) && value.ValueKind != JsonValueKind.Null ? value.GetString() : null;

    private static DateOnly Day(JsonElement row, string name) =>
        DateOnly.ParseExact(row.GetProperty(name).GetString()!, "yyyy-MM-dd", CultureInfo.InvariantCulture);

    private static DateOnly? MaybeDay(JsonElement row, string name) =>
        Text(row, name) is { } day ? DateOnly.ParseExact(day, "yyyy-MM-dd", CultureInfo.InvariantCulture) : null;
}
