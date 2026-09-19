using GoalMaker.Core.Planning;
using GoalMaker.Core.Tests.Sync;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>Habits, check-ins and pauses on a real replica (docs/habits.md, M4-04).</summary>
public sealed class HabitListTests : IDisposable
{
    private static readonly DateOnly Today = new(2026, 9, 18);
    private readonly TestReplica test = new();
    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 9, 18, 12, 0, 0, TimeSpan.Zero));
    private readonly HabitList habits;
    private readonly GoalList goals;

    public HabitListTests()
    {
        time.SetLocalTimeZone(TimeZoneInfo.Utc);
        var rows = new NewRows(test.Catalog, () => TestReplica.Owner, time);
        habits = new HabitList(test.Replica, rows, () => { });
        goals = new GoalList(test.Replica, rows, () => { });
    }

    public void Dispose() => test.Dispose();

    [Fact]
    public void AHabitKeepsOnlyTheFieldsItsCadenceAndMeasureNeed()
    {
        var daily = habits.Add(new HabitDraft("Read", Today) { Weekdays = 21, Times = 3, Emoji = " 📖 " })!;
        var weekly = habits.Add(new HabitDraft("Run", Today)
        {
            Cadence = HabitRules.PerWeek,
            Times = 3,
            Measure = HabitRules.Amount,
            Target = 5,
            Unit = " KM ",
        })!;

        Assert.Equal(("Read", "📖"), (daily.Name, daily.Emoji));
        Assert.Null(daily.Weekdays);
        Assert.Null(daily.Times);
        Assert.Null(daily.Target);
        Assert.Equal((3, 5.0, "KM"), (weekly.Times!.Value, weekly.Target!.Value, weekly.Unit));
    }

    [Fact]
    public void ACadenceWithoutItsDaysACountWithoutATargetAndABlankNameAreRefused()
    {
        Assert.Null(habits.Add(new HabitDraft("Gym", Today) { Cadence = HabitRules.OnWeekdays }));
        Assert.Null(habits.Add(new HabitDraft("Gym", Today) { Cadence = HabitRules.PerWeek, Times = 9 }));
        Assert.Null(habits.Add(new HabitDraft("Water", Today) { Measure = HabitRules.Count }));
        Assert.Null(habits.Add(new HabitDraft("Water", Today) { Measure = HabitRules.Count, Target = 0 }));
        Assert.Null(habits.Add(new HabitDraft("  ", Today)));
        Assert.Empty(habits.All());
    }

    [Fact]
    public void ADayHasOneCheckinAndTappingAgainAddsToACount()
    {
        var water = habits.Add(new HabitDraft("Water", Today) { Measure = HabitRules.Count, Target = 8, Unit = "glasses" })!;

        Assert.Equal(1, habits.CheckIn(water.Id, Today));
        Assert.Equal(3, habits.CheckIn(water.Id, Today, 2));

        var checkin = Assert.Single(habits.Checkins());
        Assert.Equal((HabitRules.CheckinId(water.Id, Today), 3.0), (checkin.Id, checkin.Value));
    }

    [Fact]
    public void ATapChecksAndUnchecksACheckHabitAndAnAmountAsksForItsValue()
    {
        var read = habits.Add(new HabitDraft("Read", Today))!;
        var run = habits.Add(new HabitDraft("Run", Today) { Measure = HabitRules.Amount, Target = 5, Unit = "km" })!;

        Assert.True(habits.Tap(read.Id, Today));
        Assert.Equal(1, habits.Checkins().Single(checkin => checkin.HabitId == read.Id).Value);
        Assert.True(habits.Tap(read.Id, Today));
        Assert.Equal(0, habits.Checkins().Single(checkin => checkin.HabitId == read.Id).Value);

        Assert.False(habits.Tap(run.Id, Today));
        Assert.DoesNotContain(habits.Checkins(), checkin => checkin.HabitId == run.Id);
    }

    [Fact]
    public void ASkipClearsTheDaysValueAndCanBeTakenBack()
    {
        var read = habits.Add(new HabitDraft("Read", Today))!;
        habits.CheckIn(read.Id, Today);

        Assert.True(habits.Skip(read.Id, Today));
        var skipped = habits.Checkins().Single();
        Assert.Equal((true, 0.0), (skipped.Skipped, skipped.Value));

        Assert.True(habits.Skip(read.Id, Today, skipped: false));
        Assert.False(habits.Checkins().Single().Skipped);
    }

    [Fact]
    public void APauseEndsTheDayBeforeTheHabitResumesAndOneResumedTheSameDayGoesAway()
    {
        var read = habits.Add(new HabitDraft("Read", Today))!;

        Assert.True(habits.Pause(read.Id, Today.AddDays(-3)));
        Assert.False(habits.Pause(read.Id, Today));
        Assert.True(habits.Resume(read.Id, Today));
        Assert.Equal(Today.AddDays(-1), habits.Pauses().Single().Until);

        Assert.True(habits.Pause(read.Id, Today));
        Assert.True(habits.Resume(read.Id, Today));
        Assert.Single(habits.Pauses());
    }

    [Fact]
    public void AnArchivedHabitStaysWithItsHistoryAndADeletedOneGoes()
    {
        var read = habits.Add(new HabitDraft("Read", Today))!;
        habits.CheckIn(read.Id, Today);

        Assert.True(habits.SetArchived(read.Id, true));
        Assert.True(habits.All().Single().Archived);
        Assert.True(habits.SetArchived(read.Id, false));
        Assert.False(habits.All().Single().Archived);

        Assert.True(habits.Delete(read.Id));
        Assert.Empty(habits.All());
        Assert.Null(habits.Find(read.Id));
    }

    [Fact]
    public void CheckinsOfAHabitInTheGoalsUnitCountTowardIt()
    {
        var goal = goals.Add(new GoalDraft("Run 80 km", GoalHorizon.Month, Today, GoalRules.ModeNumber, Target: 80, Unit: "km"))!;
        var run = habits.Add(new HabitDraft("Run", Today) { Measure = HabitRules.Amount, Target = 5, Unit = "KM", GoalId = goal.Id })!;
        var other = habits.Add(new HabitDraft("Read", Today) { Measure = HabitRules.Amount, Target = 20, Unit = "minutes", GoalId = goal.Id })!;
        habits.CheckIn(run.Id, Today, 6);
        habits.CheckIn(run.Id, Today.AddDays(-1), 4);
        habits.CheckIn(other.Id, Today, 30);

        Assert.Equal([4, 6], HabitRules.GoalAmounts(goal, habits.All(), habits.Checkins()).Order());
    }
}
