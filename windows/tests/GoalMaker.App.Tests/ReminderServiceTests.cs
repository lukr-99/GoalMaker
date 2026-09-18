using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>The PC's reminders on a real replica and fake time (docs/reminders.md).</summary>
public sealed class ReminderServiceTests : IDisposable
{
    private readonly TestPlanner planner = new();
    private readonly RecordingScheduler scheduler = new();
    private readonly ReminderService reminders;

    public ReminderServiceTests()
    {
        reminders = new ReminderService(planner.Reminders, planner.Tasks, scheduler, planner.Settings, planner.Time);
    }

    public void Dispose() => planner.Dispose();

    [Fact]
    public void AReminderKeepsItsLocalTimeThroughTheReplicaAndIsArmed()
    {
        var task = Add("Take the bread out");
        reminders.AddAt(task.Id, new DateTime(2026, 9, 18, 16, 30, 0));

        Assert.Equal(new DateTime(2026, 9, 18, 16, 30, 0), reminders.On(task.Id).Single().FireAt);
        Assert.Equal(new DateTime(2026, 9, 18, 16, 30, 0), scheduler.ArmedAt);
    }

    [Fact]
    public void ARelativeReminderCountsBackFromTheTasksTime()
    {
        var task = Add("Call the bank 17:00");
        reminders.AddBefore(task.Id, 15);

        Assert.Equal(-15, reminders.On(task.Id).Single().OffsetMinutes);
        Assert.Equal(new DateTime(2026, 9, 18, 16, 45, 0), scheduler.ArmedAt);
    }

    [Fact]
    public void CatchingUpShowsEachReminderOnce()
    {
        var task = Add("Take the bread out");
        Assert.Empty(reminders.CatchUp());
        reminders.AddAt(task.Id, new DateTime(2026, 9, 18, 14, 30, 0));

        planner.Time.Advance(TimeSpan.FromMinutes(31));

        Assert.Equal("Take the bread out", reminders.CatchUp().Single().TaskTitle);
        Assert.Empty(reminders.CatchUp());
        Assert.Null(scheduler.ArmedAt);
    }

    [Fact]
    public void AFirstLookStartsFromNowInsteadOfReplayingThePast()
    {
        var task = Add("Take the bread out");
        reminders.AddAt(task.Id, new DateTime(2026, 9, 18, 9, 0, 0));

        Assert.Empty(reminders.CatchUp());
    }

    [Fact]
    public void DoneFinishesTheTaskAndTakesTheNotificationDown()
    {
        var task = Add("Take the bread out");
        var reminder = reminders.AddAt(task.Id, new DateTime(2026, 9, 18, 13, 0, 0))!;

        reminders.Done(reminder.Id);

        Assert.Equal(TaskState.Done, planner.Task("Take the bread out").State);
        Assert.Equal(ReminderState.Done, planner.Reminders.All().Single().State);
        Assert.Equal([reminder.Id], reminders.Stale([reminder.Id]));
    }

    [Fact]
    public void SnoozingUntilTomorrowMorningArmsTheMorning()
    {
        var task = Add("Take the bread out");
        var reminder = reminders.AddAt(task.Id, new DateTime(2026, 9, 18, 13, 0, 0))!;

        reminders.Snooze(reminder.Id, Snooze.TomorrowMorning);

        Assert.Equal(new DateTime(2026, 9, 19, 8, 0, 0), planner.Reminders.All().Single().SnoozedUntil);
        Assert.Equal(new DateTime(2026, 9, 19, 8, 0, 0), scheduler.ArmedAt);
    }

    [Fact]
    public void QuietHoursHoldTheTimerBack()
    {
        planner.Settings.QuietHours = new QuietHours(new TimeOnly(14, 0), new TimeOnly(15, 0));
        var task = Add("Take the bread out");

        reminders.AddAt(task.Id, new DateTime(2026, 9, 18, 14, 30, 0));

        Assert.Equal(new DateTime(2026, 9, 18, 15, 0, 0), scheduler.ArmedAt);
    }

    [Fact]
    public void RemovingTheLastReminderCancelsTheTimer()
    {
        var task = Add("Take the bread out");
        var reminder = reminders.AddAt(task.Id, new DateTime(2026, 9, 18, 16, 30, 0))!;

        reminders.Remove(reminder.Id);

        Assert.Empty(reminders.On(task.Id));
        Assert.Null(scheduler.ArmedAt);
    }

    private TaskItem Add(string line)
    {
        var draft = ComposerParser.Parse(line, planner.Time.GetLocalNow().DateTime, planner.Settings.DayStartHour);
        return planner.Tasks.Add(draft with { PlannedDate = draft.PlannedDate ?? new DateOnly(2026, 9, 18) })!;
    }

    private sealed class RecordingScheduler : IReminderScheduler
    {
        public DateTime? ArmedAt { get; private set; }

        public void ArmAt(DateTime at) => ArmedAt = at;

        public void Cancel() => ArmedAt = null;
    }
}
