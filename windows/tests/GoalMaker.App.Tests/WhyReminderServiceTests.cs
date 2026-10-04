using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>The why reminder on the PC's one timer: when it rings, which life goal it shows, when it goes (M9-04).</summary>
public sealed class WhyReminderServiceTests : IDisposable
{
    // Monday 5 October 2026 at midnight, the start of a weekly period, so its moment is still ahead.
    private static readonly DateOnly Monday = new(2026, 10, 5);
    private readonly TestPlanner planner = new();
    private readonly Scheduler scheduler = new();
    private readonly ReminderService reminders;
    private WhyFrequency frequency = WhyFrequency.Weekly;

    public WhyReminderServiceTests()
    {
        planner.Time.SetUtcNow(At(Monday.ToDateTime(TimeOnly.MinValue)));
        reminders = new ReminderService(
            planner.Reminders, planner.Tasks, scheduler, planner.Settings, planner.Time, lifeGoals: planner.LifeGoals, whyFrequency: () => frequency);
    }

    public void Dispose() => planner.Dispose();

    [Fact]
    public void ItRingsAtTheWorkedOutMomentWithTheOpenLifeGoalAndOnlyOnce()
    {
        var audi = planner.LifeGoals.Add(new LifeGoalDraft("Own an Audi R8", "I love how it sounds"))!;
        var moment = WhyReminder.Moment(WhyFrequency.Weekly, Monday, QuietHours.Off);
        reminders.Rearm();
        Assert.Equal(moment.At, scheduler.ArmedAt);

        planner.Settings.RemindedUntil = At(Monday.ToDateTime(TimeOnly.MinValue));
        planner.Time.SetUtcNow(At(moment.At.AddMinutes(-1)));
        Assert.Null(reminders.CatchUp().Why);

        planner.Time.SetUtcNow(At(moment.At.AddMinutes(1)));
        Assert.Equal(new WhyDue(Monday, audi.Id), reminders.CatchUp().Why);
        Assert.Equal(WhyReminder.Moment(WhyFrequency.Weekly, Monday.AddDays(7), QuietHours.Off).At, scheduler.ArmedAt);

        planner.Time.SetUtcNow(At(moment.At.AddHours(1)));
        Assert.Null(reminders.CatchUp().Why);
    }

    [Fact]
    public void QuietHoursHoldItBackToTheirEnd()
    {
        var audi = planner.LifeGoals.Add(new LifeGoalDraft("Own an Audi R8", "I love how it sounds"))!;
        var moment = WhyReminder.Moment(WhyFrequency.Weekly, Monday, QuietHours.Off);
        var start = TimeOnly.FromDateTime(moment.At);
        planner.Settings.QuietHours = new QuietHours(start, start.AddHours(1));
        var held = DateOnly.FromDateTime(moment.At).ToDateTime(start.AddHours(1));
        Assert.Equal(held, WhyReminder.Moment(WhyFrequency.Weekly, Monday, planner.Settings.QuietHours).At);

        reminders.Rearm();
        Assert.Equal(held, scheduler.ArmedAt);

        planner.Settings.RemindedUntil = At(Monday.ToDateTime(TimeOnly.MinValue));
        planner.Time.SetUtcNow(At(moment.At.AddMinutes(1)));
        Assert.Null(reminders.CatchUp().Why);

        planner.Time.SetUtcNow(At(held.AddMinutes(1)));
        Assert.Equal(new WhyDue(Monday, audi.Id), reminders.CatchUp().Why);
    }

    [Fact]
    public void SwitchedOffItNeitherRingsNorArms()
    {
        frequency = WhyFrequency.Off;
        planner.LifeGoals.Add(new LifeGoalDraft("Own an Audi R8", "I love how it sounds"));
        reminders.Rearm();
        Assert.Null(scheduler.ArmedAt);

        planner.Settings.RemindedUntil = At(Monday.ToDateTime(TimeOnly.MinValue));
        planner.Time.SetUtcNow(At(Monday.AddDays(7).ToDateTime(TimeOnly.MinValue)));
        Assert.Null(reminders.CatchUp().Why);
        Assert.Null(scheduler.ArmedAt);
    }

    [Fact]
    public void WithoutAnOpenLifeGoalItNeitherRingsNorArms()
    {
        reminders.Rearm();
        Assert.Null(scheduler.ArmedAt);

        var audi = planner.LifeGoals.Add(new LifeGoalDraft("Own an Audi R8", "I love how it sounds"))!;
        planner.LifeGoals.Achieve(audi.Id);
        reminders.Rearm();
        Assert.Null(scheduler.ArmedAt);

        planner.Settings.RemindedUntil = At(Monday.ToDateTime(TimeOnly.MinValue));
        planner.Time.SetUtcNow(At(Monday.AddDays(7).ToDateTime(TimeOnly.MinValue)));
        Assert.Null(reminders.CatchUp().Why);
        Assert.Null(scheduler.ArmedAt);
    }

    [Fact]
    public void AMissedMomentIsCaughtUpOnceAndOnlyTheLatest()
    {
        var audi = planner.LifeGoals.Add(new LifeGoalDraft("Own an Audi R8", "I love how it sounds"))!;
        planner.Settings.RemindedUntil = At(Monday.ToDateTime(TimeOnly.MinValue));

        // Back three weeks later, on a Monday before ten: the latest moment is the previous week's.
        var back = Monday.AddDays(21);
        planner.Time.SetUtcNow(At(back.ToDateTime(new TimeOnly(9, 0))));
        var latest = WhyReminder.Moment(WhyFrequency.Weekly, back.AddDays(-7), QuietHours.Off);
        Assert.True(latest.At < back.ToDateTime(new TimeOnly(9, 0)));

        Assert.Equal(new WhyDue(latest.PeriodStart, audi.Id), reminders.CatchUp().Why);
        Assert.Equal(back.AddDays(-7), latest.PeriodStart);
        Assert.Equal(WhyReminder.Moment(WhyFrequency.Weekly, back, QuietHours.Off).At, scheduler.ArmedAt);
        Assert.Null(reminders.CatchUp().Why);
    }

    [Fact]
    public void AchievingDroppingOrDeletingTheLifeGoalTakesTheToastDown()
    {
        var audi = planner.LifeGoals.Add(new LifeGoalDraft("Own an Audi R8", "I love how it sounds"))!;
        Assert.False(reminders.WhyStale(audi.Id));

        planner.LifeGoals.Achieve(audi.Id);
        Assert.True(reminders.WhyStale(audi.Id));

        planner.LifeGoals.Reopen(audi.Id);
        Assert.False(reminders.WhyStale(audi.Id));

        planner.LifeGoals.Drop(audi.Id);
        Assert.True(reminders.WhyStale(audi.Id));

        planner.LifeGoals.Reopen(audi.Id);
        planner.LifeGoals.Delete(audi.Id);
        Assert.True(reminders.WhyStale(audi.Id));
        Assert.True(reminders.WhyStale("gone"));
    }

    // The planner's clock is in UTC, so a local time is the same instant.
    private static DateTimeOffset At(DateTime local) => new(local, TimeSpan.Zero);

    private sealed class Scheduler : IReminderScheduler
    {
        public DateTime? ArmedAt { get; private set; }

        public void ArmAt(DateTime at) => ArmedAt = at;

        public void Cancel() => ArmedAt = null;
    }
}
