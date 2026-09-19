using System.Globalization;
using System.Text.Json;
using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>contracts/vectors/habits.json, the same file the Android tests and the connector read.</summary>
public sealed class HabitRulesContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/habits.json").RootElement;

    [Fact]
    public void EveryDueDay()
    {
        foreach (var testCase in vectors.GetProperty("due").EnumerateArray())
        {
            Assert.True(
                testCase.GetProperty("expect").GetBoolean() == HabitRules.IsDue(Habit(testCase), Day(testCase, "day")),
                Name(testCase));
        }
    }

    [Fact]
    public void EveryPeriod()
    {
        foreach (var testCase in vectors.GetProperty("periods").EnumerateArray())
        {
            var habit = Habit(testCase);
            var start = HabitRules.PeriodStart(habit, Day(testCase, "day"));
            Assert.Equal(Day(testCase, "start"), start);
            Assert.Equal(Day(testCase, "end"), HabitRules.PeriodEnd(habit, start));
        }
    }

    [Fact]
    public void EveryState()
    {
        foreach (var testCase in vectors.GetProperty("states").EnumerateArray())
        {
            var state = HabitRules.State(Habit(testCase), Day(testCase, "period"), Day(testCase, "today"), Checkins(testCase), Pauses(testCase));
            Assert.True(testCase.GetProperty("expect").GetString() == state.ToString().ToLowerInvariant(), $"{Name(testCase)}: {state}");
        }
    }

    [Fact]
    public void EveryStreak()
    {
        foreach (var testCase in vectors.GetProperty("streaks").EnumerateArray())
        {
            var streak = HabitRules.Streak(Habit(testCase), Day(testCase, "today"), Checkins(testCase), Pauses(testCase));
            Assert.True(testCase.GetProperty("expect").GetInt32() == streak, $"{Name(testCase)}: {streak}");
        }
    }

    [Fact]
    public void EveryHeat()
    {
        foreach (var testCase in vectors.GetProperty("heat").EnumerateArray())
        {
            var heat = HabitRules.Heat(Habit(testCase), Day(testCase, "day"), Checkins(testCase), Pauses(testCase));
            var expect = testCase.GetProperty("expect");
            var expected = expect.ValueKind switch
            {
                JsonValueKind.Null => HabitHeat.None,
                JsonValueKind.String => expect.GetString() == "paused" ? HabitHeat.Paused : HabitHeat.Skipped,
                _ => HabitHeat.Share(expect.GetDouble()),
            };
            Assert.True(expected.Kind == heat.Kind && Math.Abs(expected.Fraction - heat.Fraction) < 1e-9, $"{Name(testCase)}: {heat}");
        }
    }

    [Fact]
    public void EveryRing()
    {
        foreach (var testCase in vectors.GetProperty("rings").EnumerateArray())
        {
            var ring = HabitRules.Ring(Habit(testCase), Day(testCase, "today"), Checkins(testCase));
            var expect = testCase.GetProperty("expect");
            if (expect.ValueKind == JsonValueKind.Null)
            {
                Assert.True(ring is null, $"{Name(testCase)}: {ring}");
            }
            else
            {
                Assert.True(ring is { } value && Math.Abs(expect.GetDouble() - value) < 1e-9, $"{Name(testCase)}: {ring}");
            }
        }
    }

    [Fact]
    public void EveryCheckinId()
    {
        foreach (var testCase in vectors.GetProperty("checkinIds").EnumerateArray())
        {
            Assert.Equal(
                testCase.GetProperty("expect").GetString(),
                HabitRules.CheckinId(testCase.GetProperty("habitId").GetString()!, Day(testCase, "day")));
        }
    }

    [Fact]
    public void EveryGoalAmount()
    {
        foreach (var testCase in vectors.GetProperty("goalAmounts").EnumerateArray())
        {
            var goalJson = testCase.GetProperty("goal");
            var goal = new GoalItem(
                goalJson.GetProperty("id").GetString()!,
                "Goal",
                GoalRules.HorizonOf(goalJson.GetProperty("horizon").GetString())!.Value,
                Day(goalJson, "start"))
            {
                Mode = GoalRules.ModeNumber,
                Unit = goalJson.GetProperty("unit").GetString(),
            };
            var habits = testCase.GetProperty("habits").EnumerateArray().Select(habit => new HabitItem(
                habit.GetProperty("id").GetString()!,
                "Habit",
                new DateOnly(2026, 1, 1))
            {
                Measure = habit.GetProperty("measure").GetString()!,
                Unit = habit.GetProperty("unit").GetString(),
                GoalId = habit.GetProperty("goalId").GetString(),
            });
            var expected = testCase.GetProperty("expect").EnumerateArray().Select(amount => amount.GetDouble());
            Assert.Equal(expected, HabitRules.GoalAmounts(goal, habits, Checkins(testCase)));
        }
    }

    private static HabitItem Habit(JsonElement testCase)
    {
        var habit = testCase.GetProperty("habit");
        return new HabitItem("h", "Habit", Day(habit, "startsOn"))
        {
            Cadence = habit.GetProperty("cadence").GetString()!,
            Weekdays = habit.GetProperty("weekdays").ValueKind == JsonValueKind.Null ? null : habit.GetProperty("weekdays").GetInt32(),
            Times = habit.GetProperty("times").ValueKind == JsonValueKind.Null ? null : habit.GetProperty("times").GetInt32(),
            Measure = habit.GetProperty("measure").GetString()!,
            Target = habit.GetProperty("target").ValueKind == JsonValueKind.Null ? null : habit.GetProperty("target").GetDouble(),
        };
    }

    private static List<HabitCheckin> Checkins(JsonElement testCase) =>
        testCase.GetProperty("checkins").EnumerateArray().Select((checkin, index) => new HabitCheckin(
            $"c{index}",
            checkin.TryGetProperty("habitId", out var habitId) ? habitId.GetString()! : "h",
            Day(checkin, "day"),
            checkin.GetProperty("value").GetDouble(),
            checkin.GetProperty("skipped").GetBoolean())).ToList();

    private static List<HabitPause> Pauses(JsonElement testCase) =>
        testCase.GetProperty("pauses").EnumerateArray().Select((pause, index) => new HabitPause(
            $"p{index}",
            "h",
            Day(pause, "from"),
            pause.GetProperty("until").ValueKind == JsonValueKind.Null ? null : Day(pause, "until"))).ToList();

    private static string? Name(JsonElement testCase) => testCase.GetProperty("name").GetString();

    private static DateOnly Day(JsonElement element, string name) =>
        DateOnly.ParseExact(element.GetProperty(name).GetString()!, "yyyy-MM-dd", CultureInfo.InvariantCulture);
}
