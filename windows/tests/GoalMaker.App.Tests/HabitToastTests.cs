using System.Xml.Linq;
using GoalMaker.App.Shell;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>A habit reminder's toast: what it says, its buttons and what they carry back (docs/reminders.md).</summary>
public sealed class HabitToastTests : IDisposable
{
    private const string Id = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static readonly DateOnly Day = new(2026, 9, 18);
    private readonly TestPlanner planner = new();

    public void Dispose() => planner.Dispose();

    private static XElement Toast(HabitItem habit, params HabitCheckin[] checkins) =>
        ToastReminderNotifications.HabitContent(new DueHabit(habit, Day), checkins, new TestPlanner.FormatStrings());

    private static IReadOnlyList<string> Texts(XElement toast) => [.. toast.Descendants("text").Select(text => text.Value)];

    private static IReadOnlyList<(string Label, ToastActivation? Activation)> Buttons(XElement toast) =>
        [.. toast.Descendants("action").Select(action => ((string)action.Attribute("content")!, ToastActivation.Parse((string?)action.Attribute("arguments"))))];

    [Fact]
    public void ACheckHabitIsStillToDoWithCheckInAndSkipToday()
    {
        var toast = Toast(new HabitItem(Id, "Read", Day.AddDays(-10)) { Emoji = "📖" });

        Assert.Equal(["📖 Read", "HabitReminder.Left"], Texts(toast));
        Assert.Equal(
            [
                ("HabitReminder.CheckIn", new ToastActivation(ToastAction.HabitCheckIn, Id + "/2026-09-18")),
                ("Habits.SkipDay", new ToastActivation(ToastAction.HabitSkip, Id + "/2026-09-18")),
            ],
            Buttons(toast));
        Assert.Equal(new ToastActivation(ToastAction.Habit, Id + "/2026-09-18"), ToastActivation.Parse((string?)toast.Attribute("launch")));
    }

    [Fact]
    public void ACountSaysHowFarTheDayGotAndAddsOne()
    {
        var water = new HabitItem(Id, "Water", Day.AddDays(-10)) { Measure = HabitRules.Count, Target = 8, Unit = "glasses" };
        var toast = Toast(water, new HabitCheckin("c", Id, Day, 4));

        Assert.Equal(["Water", "Habits.ValueUnit(4,8,glasses)"], Texts(toast));
        Assert.Equal(["HabitReminder.AddOne", "Habits.SkipDay"], Buttons(toast).Select(button => button.Label));
        Assert.Equal(ToastAction.HabitCheckIn, Buttons(toast)[0].Activation!.Action);
    }

    [Fact]
    public void AnAmountFillsOrLogsInTheApp()
    {
        var run = new HabitItem(Id, "Run", Day.AddDays(-10)) { Measure = HabitRules.Amount, Target = 5 };
        var toast = Toast(run, new HabitCheckin("c", Id, Day, 2));

        Assert.Equal("Habits.Value(2,5)", Texts(toast)[1]);
        Assert.Equal(["HabitReminder.Fill", "HabitReminder.Log", "Habits.SkipDay"], Buttons(toast).Select(button => button.Label));
        Assert.Equal(new ToastActivation(ToastAction.HabitFill, Id + "/2026-09-18"), Buttons(toast)[0].Activation);
        Assert.Equal(ToastAction.HabitLog, Buttons(toast)[1].Activation!.Action);
    }

    [Fact]
    public void AnAmountLimitOnlyLogs()
    {
        var coffee = new HabitItem(Id, "Coffee", Day.AddDays(-10)) { Measure = HabitRules.Amount, Target = 0.5, Direction = HabitRules.AtMost };

        Assert.Equal(["HabitReminder.Log", "Habits.SkipDay"], Buttons(Toast(coffee)).Select(button => button.Label));
    }

    [Fact]
    public void AWeeklyHabitCountsTheWeekAndSkipsTheWeek()
    {
        var swim = new HabitItem(Id, "Swim", Day.AddDays(-30)) { Cadence = HabitRules.PerWeek, Times = 3 };
        var toast = Toast(swim, new HabitCheckin("c", Id, Day.AddDays(-3), 1), new HabitCheckin("old", Id, Day.AddDays(-7), 1));

        Assert.Equal("Habits.MetWeek(1,3)", Texts(toast)[1]);
        Assert.Equal(["HabitReminder.CheckIn", "Habits.SkipWeek"], Buttons(toast).Select(button => button.Label));
    }

    [Fact]
    public void AMonthlyHabitSkipsTheMonth()
    {
        var toast = Toast(new HabitItem(Id, "Call home", Day.AddDays(-30)) { Cadence = HabitRules.PerMonth, Times = 2 });

        Assert.Equal("Habits.MetMonth(0,2)", Texts(toast)[1]);
        Assert.Equal("Habits.SkipMonth", Buttons(toast)[1].Label);
    }

    [Fact]
    public void TheCheckInButtonChecksTheHabitInAndMakesItsToastStale()
    {
        var reminders = new ReminderService(planner.Reminders, planner.Tasks, new NoScheduler(), planner.Settings, planner.Time, habits: planner.Habits);
        var read = planner.Habits.Add(new HabitDraft("Read", Day.AddDays(-10)) { RemindAt = new TimeOnly(19, 30) })!;
        var button = Buttons(Toast(read))[0].Activation!;
        var (habitId, day) = button.Habit();
        Assert.False(reminders.HabitStale(habitId, day));

        reminders.CheckInHabit(habitId, day);

        Assert.Equal((read.Id, Day), (habitId, day));
        Assert.True(reminders.HabitStale(habitId, day));
    }

    [Fact]
    public void TheSkipButtonSkipsTheDay()
    {
        var reminders = new ReminderService(planner.Reminders, planner.Tasks, new NoScheduler(), planner.Settings, planner.Time, habits: planner.Habits);
        var read = planner.Habits.Add(new HabitDraft("Read", Day.AddDays(-10)) { RemindAt = new TimeOnly(19, 30) })!;
        var (habitId, day) = Buttons(Toast(read))[1].Activation!.Habit();

        reminders.SkipHabit(habitId, day);

        Assert.True(planner.Habits.Checkins().Single(checkin => checkin.HabitId == read.Id).Skipped);
        Assert.True(reminders.HabitStale(habitId, day));
    }

    private sealed class NoScheduler : IReminderScheduler
    {
        public void ArmAt(DateTime at)
        {
        }

        public void Cancel()
        {
        }
    }
}
