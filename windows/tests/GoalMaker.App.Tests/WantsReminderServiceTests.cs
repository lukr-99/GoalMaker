using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>The wants toast on the PC's one timer: when it rings, what it names, when it goes (M8-05).</summary>
public sealed class WantsReminderServiceTests : IDisposable
{
    private readonly TestPlanner planner = new();
    private readonly Scheduler scheduler = new();
    private readonly ReminderService reminders;

    public WantsReminderServiceTests()
    {
        planner.Time.SetUtcNow(new DateTimeOffset(2026, 9, 28, 12, 0, 0, TimeSpan.Zero));
        planner.Settings.WantsReadyReminder = new TimeOnly(10, 0);
        reminders = new ReminderService(planner.Reminders, planner.Tasks, scheduler, planner.Settings, planner.Time, wants: planner.Wants);
    }

    public void Dispose() => planner.Dispose();

    [Fact]
    public void TheTimerWaitsForTheMorningAWantCoolsAndTheLookNamesItOnce()
    {
        var lamp = planner.Wants.Add(new WantDraft("Lamp", "Dark desk", Price: 450))!;
        reminders.Rearm();
        Assert.Equal(new DateTime(2026, 10, 5, 10, 0, 0), scheduler.ArmedAt);

        planner.Settings.RemindedUntil = new DateTimeOffset(2026, 10, 5, 9, 0, 0, TimeSpan.Zero);
        planner.Time.SetUtcNow(new DateTimeOffset(2026, 10, 5, 10, 0, 1, TimeSpan.Zero));
        Assert.Equal(new WantsDue(new DateOnly(2026, 10, 5), [lamp.Id]), reminders.CatchUp().Wants);

        planner.Time.SetUtcNow(new DateTimeOffset(2026, 10, 5, 11, 0, 0, TimeSpan.Zero));
        Assert.Null(reminders.CatchUp().Wants);
    }

    [Fact]
    public void DecidingEveryWantItNamedTakesTheToastDown()
    {
        var kindle = planner.Wants.Add(new WantDraft("Kindle", "Reading at night", PickedDays: 0))!;
        var shoes = planner.Wants.Add(new WantDraft("Trail shoes", "Holes", PickedDays: 0))!;

        Assert.False(reminders.WantsStale([kindle.Id, shoes.Id]));
        planner.Wants.Decide(kindle.Id, WantRules.Bought);
        Assert.False(reminders.WantsStale([kindle.Id, shoes.Id]));
        planner.Wants.Delete(shoes.Id);
        Assert.True(reminders.WantsStale([kindle.Id, shoes.Id]));
    }

    [Fact]
    public void SwitchedOffItNeitherRingsNorArms()
    {
        planner.Settings.WantsReadyReminder = null;
        planner.Wants.Add(new WantDraft("Lamp", "Dark desk", Price: 450));
        reminders.Rearm();
        Assert.Null(scheduler.ArmedAt);

        planner.Settings.RemindedUntil = new DateTimeOffset(2026, 10, 5, 9, 0, 0, TimeSpan.Zero);
        planner.Time.SetUtcNow(new DateTimeOffset(2026, 10, 5, 10, 0, 1, TimeSpan.Zero));
        Assert.Null(reminders.CatchUp().Wants);
    }

    [Fact]
    public void ANeedNeverArmsTheTimerNorRingsWithTheWants()
    {
        // Early on the day it is added, before the wants' time, so a want ready today would arm it.
        planner.Time.SetUtcNow(new DateTimeOffset(2026, 9, 29, 7, 0, 0, TimeSpan.Zero));
        planner.Settings.RemindedUntil = new DateTimeOffset(2026, 9, 29, 7, 0, 0, TimeSpan.Zero);
        planner.Wants.Add(new WantDraft("Winter tyres", string.Empty, Price: 12900, Kind: WantRules.Need));

        reminders.Rearm();
        Assert.Null(scheduler.ArmedAt);

        planner.Time.SetUtcNow(new DateTimeOffset(2026, 9, 29, 10, 0, 1, TimeSpan.Zero));
        Assert.Null(reminders.CatchUp().Wants);
    }

    private sealed class Scheduler : IReminderScheduler
    {
        public DateTime? ArmedAt { get; private set; }

        public void ArmAt(DateTime at) => ArmedAt = at;

        public void Cancel() => ArmedAt = null;
    }
}
