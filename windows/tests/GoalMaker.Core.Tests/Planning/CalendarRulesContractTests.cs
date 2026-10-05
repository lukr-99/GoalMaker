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
                AreaId = Text(task, "area"),
                ProjectId = Text(task, "project"),
            }).ToList();
            var links = testCase.GetProperty("tasks").EnumerateArray().ToDictionary(
                task => task.GetProperty("id").GetString()!,
                task => (IReadOnlySet<string>)(task.TryGetProperty("tags", out var tags) ? tags.EnumerateArray().Select(tag => tag.GetString()!).ToHashSet() : []));
            var projectAreas = (testCase.TryGetProperty("projects", out var projects) ? projects.EnumerateArray().ToList() : [])
                .ToDictionary(project => project.GetProperty("id").GetString()!, project => Text(project, "area"));
            var filter = new ListFilter(Text(testCase, "area"), Text(testCase, "tag"));
            var reminders = testCase.GetProperty("reminders").EnumerateArray().Select((reminder, index) => new ReminderItem(
                $"r{index}",
                reminder.GetProperty("taskId").GetString()!,
                ReminderState.Pending)
            {
                FireAt = DateTime.Parse(reminder.GetProperty("at").GetString()!, CultureInfo.InvariantCulture),
            }).ToList();

            var days = CalendarRules.Build(tasks, reminders, Day(testCase, "from"), Day(testCase, "to"), task => filter.Keeps(task, links, projectAreas));
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

    [Fact]
    public void EveryDaysEvents()
    {
        foreach (var testCase in vectors.GetProperty("eventDays").EnumerateArray())
        {
            var name = testCase.GetProperty("name").GetString();
            var filter = new ListFilter(Text(testCase, "area"), Text(testCase, "tag"));
            var days = EventRules.Days(Events(testCase), Day(testCase, "from"), Day(testCase, "to"), filter);
            var expect = testCase.GetProperty("expect");
            Assert.True(expect.EnumerateObject().Count() == days.Count, $"{name}: {days.Count} days");
            foreach (var (day, events) in days)
            {
                var row = expect.GetProperty(day.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture));
                Assert.True(
                    row.EnumerateArray().Select(id => id.GetString()).SequenceEqual(events.Select(item => item.Id)),
                    $"{name} {day}: {string.Join(", ", events.Select(item => item.Id))}");
            }
        }
    }

    [Fact]
    public void EveryGridsBars()
    {
        foreach (var testCase in vectors.GetProperty("bars").EnumerateArray())
        {
            var name = testCase.GetProperty("name").GetString();
            var rows = EventRules.Bars(Events(testCase), Day(testCase, "start"), Day(testCase, "end"));
            var expect = testCase.GetProperty("rows").EnumerateArray().ToList();
            Assert.True(expect.Count == rows.Count, $"{name}: {rows.Count} rows");
            for (var index = 0; index < rows.Count; index++)
            {
                var wanted = expect[index].EnumerateArray().Select(bar => (
                    bar.GetProperty("id").GetString(),
                    bar.GetProperty("from").GetInt32(),
                    bar.GetProperty("to").GetInt32(),
                    bar.GetProperty("lane").GetInt32(),
                    bar.GetProperty("before").GetBoolean(),
                    bar.GetProperty("after").GetBoolean())).ToList();
                var got = rows[index].Select(bar => ((string?)bar.Event.Id, bar.From, bar.To, bar.Lane, bar.Before, bar.After)).ToList();
                Assert.True(wanted.SequenceEqual(got), $"{name}, row {index}: {string.Join("; ", got)}");
            }
        }
    }

    [Fact]
    public void EveryOngoingDay()
    {
        foreach (var testCase in vectors.GetProperty("ongoing").EnumerateArray())
        {
            var name = testCase.GetProperty("name").GetString();
            var ongoing = EventRules.Ongoing(Events(testCase), Day(testCase, "day"));
            var wanted = testCase.GetProperty("expect").EnumerateArray().Select(item => (
                item.GetProperty("id").GetString(),
                item.GetProperty("dayOf").GetInt32(),
                item.GetProperty("days").GetInt32())).ToList();
            var got = ongoing.Select(item => ((string?)item.Event.Id, item.DayOf, item.Days)).ToList();
            Assert.True(wanted.SequenceEqual(got), $"{name}: {string.Join("; ", got)}");
        }
    }

    private static List<EventItem> Events(JsonElement testCase) =>
    [
        .. testCase.GetProperty("events").EnumerateArray().Select(item => new EventItem(
            item.GetProperty("id").GetString()!,
            item.GetProperty("title").GetString()!,
            Day(item, "startsOn"),
            Day(item, "endsOn"),
            AreaId: Text(item, "area"),
            Deleted: item.GetProperty("deleted").GetBoolean())),
    ];

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
