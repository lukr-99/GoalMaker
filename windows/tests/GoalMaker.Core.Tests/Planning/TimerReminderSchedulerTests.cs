using GoalMaker.Infrastructure.Planning;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>The PC's one reminder timer (docs/reminders.md).</summary>
public sealed class TimerReminderSchedulerTests
{
    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 9, 18, 14, 0, 0, TimeSpan.Zero));
    private int fired;

    public TimerReminderSchedulerTests() => time.SetLocalTimeZone(TimeZoneInfo.Utc);

    [Fact]
    public void GoesOffAtTheArmedMinute()
    {
        using var scheduler = new TimerReminderScheduler(time, () => fired++);
        scheduler.ArmAt(new DateTime(2026, 9, 18, 14, 10, 0));

        time.Advance(TimeSpan.FromMinutes(9));
        Assert.Equal(0, fired);
        time.Advance(TimeSpan.FromMinutes(1));
        Assert.Equal(1, fired);
    }

    [Fact]
    public void ArmingAgainReplacesTheTimer()
    {
        using var scheduler = new TimerReminderScheduler(time, () => fired++);
        scheduler.ArmAt(new DateTime(2026, 9, 18, 14, 10, 0));
        scheduler.ArmAt(new DateTime(2026, 9, 18, 14, 30, 0));

        time.Advance(TimeSpan.FromMinutes(10));
        Assert.Equal(0, fired);
        time.Advance(TimeSpan.FromMinutes(20));
        Assert.Equal(1, fired);
    }

    [Fact]
    public void ATimeAlreadyPassedGoesOffAtOnce()
    {
        using var scheduler = new TimerReminderScheduler(time, () => fired++);
        scheduler.ArmAt(new DateTime(2026, 9, 18, 13, 0, 0));

        time.Advance(TimeSpan.Zero);
        Assert.Equal(1, fired);
    }

    [Fact]
    public void AFarReminderIsCheckedAgainAfterADay()
    {
        using var scheduler = new TimerReminderScheduler(time, () => fired++);
        scheduler.ArmAt(new DateTime(2026, 10, 18, 14, 0, 0));

        time.Advance(TimeSpan.FromDays(1));
        Assert.Equal(1, fired);
    }

    [Fact]
    public void CancelledTimersStayQuiet()
    {
        using var scheduler = new TimerReminderScheduler(time, () => fired++);
        scheduler.ArmAt(new DateTime(2026, 9, 18, 14, 10, 0));
        scheduler.Cancel();

        time.Advance(TimeSpan.FromHours(1));
        Assert.Equal(0, fired);
    }
}
