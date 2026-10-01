using GoalMaker.App.ViewModels;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>
/// Today's habits panel beside the tasks (the habits redesign): only habits on Today, Hide done, what is
/// left, the all done card, and checking in and skipping from a card.
/// </summary>
public sealed class TodayHabitsTests : IDisposable
{
    private static readonly DateOnly Today = new(2026, 9, 18);
    private readonly TestPlanner planner = new();

    public void Dispose() => planner.Dispose();

    [Fact]
    public void OnlyTheHabitsOnTodayShowInThePanel()
    {
        planner.Habits.Add(new HabitDraft("Read", Today));
        planner.Habits.Add(new HabitDraft("Floss", Today) { ShowOnToday = false });
        // Monday and Wednesday only; the 18th is a Friday.
        planner.Habits.Add(new HabitDraft("Gym", Today) { Cadence = HabitRules.OnWeekdays, Weekdays = 5 });

        var (today, _) = List();

        Assert.Equal(["Read"], today.Habits.Select(row => row.Name));
        Assert.True(today.HasHabits);
        Assert.Equal("HABITS.TODAYLEFT(1)", today.HabitsHeader);
        Assert.False(today.Habits.Single().IsFull);
    }

    [Fact]
    public void NoHabitsOnTodayLeavesThePanelOut()
    {
        planner.Habits.Add(new HabitDraft("Floss", Today) { ShowOnToday = false });

        var (today, _) = List();

        Assert.False(today.HasHabits);
    }

    [Fact]
    public void HideDoneHidesTheDoneHabitsAndKeepsTheRest()
    {
        var read = planner.Habits.Add(new HabitDraft("Read", Today))!;
        planner.Habits.Add(new HabitDraft("Water", Today) { Measure = HabitRules.Count, Target = 8, Unit = "glasses" });
        planner.Habits.CheckIn(read.Id, Today);
        var (today, _) = List();

        today.HideDoneHabits = true;

        Assert.Equal(["Water"], today.ShownHabits.Select(row => row.Name));
        Assert.Equal(2, today.Habits.Count);
        Assert.Equal("Habits.ShowDone", today.HideDoneText);
        Assert.False(today.HabitsAllDone);

        today.HideDoneHabits = false;
        Assert.Equal(["Read", "Water"], today.ShownHabits.Select(row => row.Name));
    }

    [Fact]
    public void EveryHabitDoneShowsTheAllDoneCardAndALimitNeverHoldsItUp()
    {
        var read = planner.Habits.Add(new HabitDraft("Read", Today))!;
        planner.Habits.Add(new HabitDraft("Snacks", Today) { Measure = HabitRules.Count, Target = 2, Direction = HabitRules.AtMost });
        var (today, _) = List();
        Assert.False(today.HabitsAllDone);

        today.Habits.Single(row => row.Name == "Read").CheckInCommand.Execute(null);

        Assert.True(today.HabitsAllDone);
        Assert.Equal("HABITS.TODAYDONE", today.HabitsHeader);
        Assert.Equal(1.0, planner.Habits.Checkins().Single(checkin => checkin.HabitId == read.Id).Value);
    }

    [Fact]
    public void TheSubtitleCountsOnlyTheHabitsLeft()
    {
        var read = planner.Habits.Add(new HabitDraft("Read", Today))!;
        planner.Habits.Add(new HabitDraft("Stretch", Today));
        planner.Habits.Add(new HabitDraft("Snacks", Today) { Measure = HabitRules.Count, Target = 2, Direction = HabitRules.AtMost });
        planner.Habits.Skip(read.Id, Today);

        var (today, _) = List();

        Assert.EndsWith("Habits.Left(1))", today.Subtitle, StringComparison.Ordinal);
    }

    [Fact]
    public void ACardSkipsFromItsMenuAndItsButtonTakesTheSkipBack()
    {
        var stretch = planner.Habits.Add(new HabitDraft("Stretch", Today))!;
        var (today, _) = List();

        today.Habits.Single().SkipCommand.Execute(null);
        var skipped = today.Habits.Single();
        Assert.Equal(HabitStanding.Skipped, skipped.Standing);
        Assert.Equal("Habits.UnskipOn(Stretch)", skipped.ButtonText);
        Assert.True(planner.Habits.Checkins().Single(checkin => checkin.HabitId == stretch.Id).Skipped);

        skipped.ButtonCommand.Execute(null);

        Assert.Equal(HabitStanding.Left, today.Habits.Single().Standing);
        Assert.False(planner.Habits.Checkins().Single().Skipped);
    }

    [Fact]
    public void ACardOnTodayAsksForTheHabitsPage()
    {
        planner.Habits.Add(new HabitDraft("Read", Today));
        var (today, habits) = List();
        var asked = 0;
        habits.PageWanted += (_, _) => asked++;

        today.Habits.Single().OpenHabitsCommand.Execute(null);
        today.Habits.Single().EditCommand.Execute(null);

        Assert.Equal(2, asked);
        Assert.True(habits.Editor.IsOpen);
    }

    private (ListViewModel Today, HabitsViewModel Habits) List()
    {
        var habits = new HabitsViewModel(planner.Habits, planner.Goals, planner.Settings, planner.Strings, planner.Time, () => true, action => action());
        var composer = new ComposerViewModel(
            planner.Tasks, planner.Areas, planner.Tags, planner.Projects, planner.Settings, planner.Strings, planner.Time, _ => null, day => day, action => action());
        var today = new ListViewModel(
            ListKind.Today,
            planner.Tasks,
            planner.Areas,
            composer,
            planner.Sync,
            planner.Settings,
            planner.Strings,
            planner.Time,
            _ => null,
            () => true,
            planner.Tick,
            action => action(),
            habitsPage: habits,
            habitList: planner.Habits);
        return (today, habits);
    }
}
