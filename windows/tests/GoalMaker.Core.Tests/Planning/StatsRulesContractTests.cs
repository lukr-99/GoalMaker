using System.Globalization;
using System.Text.Json;
using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>contracts/vectors/stats.json, the same file the Android tests read.</summary>
public sealed class StatsRulesContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/stats.json").RootElement;

    [Fact]
    public void EveryWeekOfFinishedTasks()
    {
        foreach (var testCase in vectors.GetProperty("weeks").EnumerateArray())
        {
            var name = Name(testCase);
            var weeks = StatsRules.WeeksDone(Tasks(testCase), Day(testCase, "today"), testCase.GetProperty("count").GetInt32());
            var expect = testCase.GetProperty("expect").EnumerateArray().ToList();
            Assert.Equal(expect.Count, weeks.Count);
            for (var index = 0; index < expect.Count; index++)
            {
                Assert.True(Day(expect[index], "start") == weeks[index].Start, name);
                Assert.True(expect[index].GetProperty("done").GetInt32() == weeks[index].Done, $"{name}: {weeks[index].Start}");
            }

            var digest = new StatsDigest { Weeks = weeks };
            Assert.True(testCase.GetProperty("done").GetInt32() == digest.Done, $"{name}: total");
            Assert.True(Maybe(testCase, "best") == digest.BestWeek?.Start, $"{name}: best");
        }
    }

    [Fact]
    public void EveryMonthOfGoals()
    {
        foreach (var testCase in vectors.GetProperty("months").EnumerateArray())
        {
            var name = Name(testCase);
            var months = StatsRules.MonthsHit(
                Goals(testCase),
                Tasks(testCase),
                Entries(testCase),
                Habits(testCase),
                Checkins(testCase),
                Day(testCase, "today"),
                testCase.GetProperty("count").GetInt32());
            var expect = testCase.GetProperty("expect").EnumerateArray().ToList();
            Assert.Equal(expect.Count, months.Count);
            for (var index = 0; index < expect.Count; index++)
            {
                Assert.True(Day(expect[index], "start") == months[index].Start, name);
                Assert.True(expect[index].GetProperty("hit").GetInt32() == months[index].Hit, $"{name}: hit {months[index].Start}");
                Assert.True(expect[index].GetProperty("total").GetInt32() == months[index].Total, $"{name}: total {months[index].Start}");
            }

            var digest = new StatsDigest { Months = months };
            Assert.True(testCase.GetProperty("hit").GetInt32() == digest.GoalsHit, $"{name}: hit");
            Assert.True(testCase.GetProperty("total").GetInt32() == digest.GoalsTotal, $"{name}: total");
        }
    }

    [Fact]
    public void EveryHabitOverTheWindow()
    {
        foreach (var testCase in vectors.GetProperty("habits").EnumerateArray())
        {
            var name = Name(testCase);
            var rows = StatsRules.HabitRows(
                Habits(testCase),
                Checkins(testCase),
                Pauses(testCase),
                Day(testCase, "today"),
                testCase.GetProperty("weeks").GetInt32());
            var expect = testCase.GetProperty("expect").EnumerateArray().ToList();
            Assert.Equal(expect.Count, rows.Count);
            for (var index = 0; index < expect.Count; index++)
            {
                Assert.True(expect[index].GetProperty("id").GetString() == rows[index].Id, name);
                Assert.True(expect[index].GetProperty("met").GetInt32() == rows[index].Met, $"{name}: met {rows[index].Met}");
                Assert.True(expect[index].GetProperty("periods").GetInt32() == rows[index].Periods, $"{name}: periods {rows[index].Periods}");
                Assert.True(expect[index].GetProperty("streak").GetInt32() == rows[index].Streak, $"{name}: streak {rows[index].Streak}");
                Assert.True(expect[index].GetProperty("best").GetInt32() == rows[index].Best, $"{name}: best {rows[index].Best}");
            }
        }
    }

    [Fact]
    public void EveryChartOfRatings()
    {
        foreach (var testCase in vectors.GetProperty("ratings").EnumerateArray())
        {
            var name = Name(testCase);
            var reviews = testCase.GetProperty("reviews").EnumerateArray().Select((review, index) =>
                new ReviewItem($"r{index}", review.GetProperty("kind").GetString()!, Day(review, "periodStart"))
                {
                    Mood = Number(review, "mood"),
                    Energy = Number(review, "energy"),
                    Deleted = Flag(review, "deleted"),
                }).ToList();
            var ratings = StatsRules.RatingRows(reviews, testCase.GetProperty("kind").GetString()!, testCase.GetProperty("count").GetInt32());
            var expect = testCase.GetProperty("expect").EnumerateArray().ToList();
            Assert.Equal(expect.Count, ratings.Count);
            for (var index = 0; index < expect.Count; index++)
            {
                Assert.True(Day(expect[index], "periodStart") == ratings[index].PeriodStart, name);
                Assert.True(Number(expect[index], "mood") == ratings[index].Mood, $"{name}: mood");
                Assert.True(Number(expect[index], "energy") == ratings[index].Energy, $"{name}: energy");
            }
        }
    }

    [Fact]
    public void EveryLookBack()
    {
        foreach (var testCase in vectors.GetProperty("lookBack").EnumerateArray())
        {
            var name = Name(testCase);
            var kind = testCase.GetProperty("kind").GetString()!;
            var periodStart = Day(testCase, "periodStart");
            var today = Day(testCase, "today");
            var areas = testCase.GetProperty("areas").EnumerateArray()
                .Select(area => new AreaItem(area.GetProperty("id").GetString()!, area.GetProperty("name").GetString()!, "blue", null));
            var digest = ReviewLookBack.Build(
                kind,
                periodStart,
                Tasks(testCase),
                areas,
                Goals(testCase),
                Entries(testCase),
                Habits(testCase),
                Checkins(testCase),
                Pauses(testCase),
                today);
            var expect = testCase.GetProperty("expect");

            Assert.True(Day(expect, "periodEnd") == digest.PeriodEnd, $"{name}: end");
            Assert.True(expect.GetProperty("done").GetInt32() == digest.Done, $"{name}: done {digest.Done}");
            Assert.True(expect.GetProperty("doneBefore").GetInt32() == digest.DoneBefore, $"{name}: before {digest.DoneBefore}");
            Assert.True(expect.GetProperty("change").GetInt32() == digest.Change, $"{name}: change {digest.Change}");

            var days = expect.GetProperty("days");
            Assert.True(days.GetProperty("count").GetInt32() == digest.Days.Count, $"{name}: days {digest.Days.Count}");
            var byDay = days.GetProperty("done");
            foreach (var day in digest.Days)
            {
                var done = byDay.TryGetProperty(day.Date.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture), out var count) ? count.GetInt32() : 0;
                Assert.True(done == day.Done, $"{name}: {day.Date} {day.Done}");
            }

            if (expect.GetProperty("bestDay").ValueKind == JsonValueKind.Null)
            {
                Assert.True(digest.BestDay is null, $"{name}: best day");
            }
            else
            {
                Assert.True(Day(expect.GetProperty("bestDay"), "day") == digest.BestDay?.Date, $"{name}: best day");
                Assert.True(expect.GetProperty("bestDay").GetProperty("done").GetInt32() == digest.BestDay?.Done, $"{name}: best day");
            }

            if (expect.GetProperty("strongestArea").ValueKind == JsonValueKind.Null)
            {
                Assert.True(digest.StrongestArea is null, $"{name}: area");
            }
            else
            {
                Assert.True(expect.GetProperty("strongestArea").GetProperty("name").GetString() == digest.StrongestArea?.Name, $"{name}: area");
                Assert.True(expect.GetProperty("strongestArea").GetProperty("done").GetInt32() == digest.StrongestArea?.Done, $"{name}: area");
            }

            var goals = expect.GetProperty("goals").EnumerateArray().ToList();
            Assert.Equal(goals.Count, digest.Goals.Count);
            for (var index = 0; index < goals.Count; index++)
            {
                Assert.True(goals[index].GetProperty("id").GetString() == digest.Goals[index].Id, $"{name}: goal");
                Assert.True(Math.Abs(goals[index].GetProperty("fraction").GetDouble() - digest.Goals[index].Fraction) < 1e-9, $"{name}: fraction");
                Assert.True(goals[index].GetProperty("hit").GetBoolean() == digest.Goals[index].Hit, $"{name}: hit");
            }

            var habits = expect.GetProperty("habits").EnumerateArray().ToList();
            Assert.Equal(habits.Count, digest.Habits.Count);
            for (var index = 0; index < habits.Count; index++)
            {
                Assert.True(habits[index].GetProperty("id").GetString() == digest.Habits[index].Id, $"{name}: habit");
                Assert.True(habits[index].GetProperty("met").GetInt32() == digest.Habits[index].Met, $"{name}: met");
                Assert.True(habits[index].GetProperty("periods").GetInt32() == digest.Habits[index].Periods, $"{name}: periods");
                Assert.True(habits[index].GetProperty("streak").GetInt32() == digest.Habits[index].Streak, $"{name}: streak");
            }

            Assert.Equal(
                expect.GetProperty("openTasks").EnumerateArray().Select(task => task.GetString()),
                digest.OpenTasks.Select(task => task.Id));

            var expected = expect.GetProperty("expected").GetDouble();
            Assert.True(Math.Abs(expected - ReviewLookBack.Expected(periodStart, digest.PeriodEnd, today)) < 1e-9, $"{name}: expected");

            var facts = expect.GetProperty("facts");
            Assert.True(facts.GetProperty("doneTasks").GetInt32() == digest.Facts.DoneTasks, $"{name}: facts done");
            Assert.True(Math.Abs(facts.GetProperty("averageDone").GetDouble() - digest.Facts.AverageDone) < 1e-9, $"{name}: facts average");
            Assert.Equal(
                facts.GetProperty("taskMoves").EnumerateArray().Select(moves => moves.GetInt32()),
                digest.Facts.Tasks.Select(task => task.Moves));
            Assert.Equal(
                facts.GetProperty("habitsMissed").EnumerateArray().Select(missed => missed.GetInt32()),
                digest.Facts.Habits.Select(habit => habit.Missed));
            foreach (var goal in digest.Facts.Goals)
            {
                Assert.True(Math.Abs(expected - goal.Expected) < 1e-9, $"{name}: fact expected");
            }
        }
    }

    private static IReadOnlyList<TaskItem> Tasks(JsonElement testCase) => Rows(testCase, "tasks").Select(task => new TaskItem(
        task.GetProperty("id").GetString()!,
        Text(task, "title") ?? string.Empty,
        State(Text(task, "state")),
        false,
        "2026-01-01T00:00:00Z")
    {
        PlannedDate = MaybeDay(task, "plannedDate"),
        AreaId = Text(task, "areaId"),
        Deleted = Flag(task, "deleted"),
        CompletedAt = Text(task, "completedAt"),
        GoalId = Text(task, "goalId"),
        MovedCount = Number(task, "movedCount") ?? 0,
    }).ToList();

    private static IReadOnlyList<GoalItem> Goals(JsonElement testCase) => Rows(testCase, "goals").Select(goal => new GoalItem(
        goal.GetProperty("id").GetString()!,
        Text(goal, "title") ?? string.Empty,
        GoalRules.HorizonOf(Text(goal, "horizon"))!.Value,
        Day(goal, "periodStart"))
    {
        Mode = Text(goal, "mode") ?? GoalRules.ModeDone,
        Status = Text(goal, "status") ?? GoalRules.Open,
        Target = Amount(goal, "target"),
        Unit = Text(goal, "unit"),
        Deleted = Flag(goal, "deleted"),
    }).ToList();

    private static IReadOnlyList<GoalEntryItem> Entries(JsonElement testCase) => Rows(testCase, "entries")
        .Select((entry, index) => new GoalEntryItem($"e{index}", entry.GetProperty("goalId").GetString()!, new DateOnly(2026, 1, 1), entry.GetProperty("amount").GetDouble()))
        .ToList();

    private static IReadOnlyList<HabitItem> Habits(JsonElement testCase) => Rows(testCase, "habits").Select(habit => new HabitItem(
        habit.GetProperty("id").GetString()!,
        Text(habit, "name") ?? string.Empty,
        Day(habit, "startsOn"))
    {
        Cadence = Text(habit, "cadence") ?? HabitRules.Daily,
        Weekdays = Number(habit, "weekdays"),
        Times = Number(habit, "times"),
        Measure = Text(habit, "measure") ?? HabitRules.Check,
        Target = Amount(habit, "target"),
        Unit = Text(habit, "unit"),
        GoalId = Text(habit, "goalId"),
        Archived = Flag(habit, "archived"),
    }).ToList();

    private static IReadOnlyList<HabitCheckin> Checkins(JsonElement testCase) => Rows(testCase, "checkins").Select((checkin, index) => new HabitCheckin(
        $"c{index}",
        checkin.GetProperty("habitId").GetString()!,
        Day(checkin, "day"),
        checkin.GetProperty("value").GetDouble())
    {
        Skipped = Flag(checkin, "skipped"),
    }).ToList();

    private static IReadOnlyList<HabitPause> Pauses(JsonElement testCase) => Rows(testCase, "pauses").Select((pause, index) => new HabitPause(
        $"p{index}",
        pause.GetProperty("habitId").GetString()!,
        Day(pause, "from"),
        MaybeDay(pause, "until"))).ToList();

    private static IEnumerable<JsonElement> Rows(JsonElement testCase, string name) =>
        testCase.TryGetProperty(name, out var rows) ? rows.EnumerateArray() : [];

    private static TaskState State(string? name) => name switch
    {
        "done" => TaskState.Done,
        "dropped" => TaskState.Dropped,
        _ => TaskState.Open,
    };

    private static string Name(JsonElement testCase) => Text(testCase, "name") ?? string.Empty;

    private static string? Text(JsonElement row, string name) =>
        row.TryGetProperty(name, out var value) && value.ValueKind != JsonValueKind.Null ? value.GetString() : null;

    private static int? Number(JsonElement row, string name) =>
        row.TryGetProperty(name, out var value) && value.ValueKind != JsonValueKind.Null ? value.GetInt32() : null;

    private static double? Amount(JsonElement row, string name) =>
        row.TryGetProperty(name, out var value) && value.ValueKind != JsonValueKind.Null ? value.GetDouble() : null;

    private static bool Flag(JsonElement row, string name) =>
        row.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.True;

    private static DateOnly Day(JsonElement row, string name) =>
        DateOnly.ParseExact(row.GetProperty(name).GetString()!, "yyyy-MM-dd", CultureInfo.InvariantCulture);

    private static DateOnly? MaybeDay(JsonElement row, string name) =>
        Text(row, name) is { } day ? DateOnly.ParseExact(day, "yyyy-MM-dd", CultureInfo.InvariantCulture) : null;

    private static DateOnly? Maybe(JsonElement testCase, string name) => MaybeDay(testCase, name);
}
