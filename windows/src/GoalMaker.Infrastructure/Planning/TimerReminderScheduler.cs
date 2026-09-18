using GoalMaker.Core.Planning;

namespace GoalMaker.Infrastructure.Planning;

/// <summary>
/// The PC's one reminder timer, kept by the tray app (docs/reminders.md). When it goes off it calls
/// <c>onDue</c>, which shows what arrived and arms the next one. A timer never waits more than a day,
/// so a long wait is checked again rather than trusted across sleeps and clock changes.
/// </summary>
public sealed class TimerReminderScheduler(TimeProvider time, Action onDue) : IReminderScheduler, IDisposable
{
    private static readonly TimeSpan Longest = TimeSpan.FromDays(1);
    private readonly Lock gate = new();
    private ITimer? timer;

    public void ArmAt(DateTime at)
    {
        var wait = at - time.GetLocalNow().DateTime;
        wait = wait < TimeSpan.Zero ? TimeSpan.Zero : wait > Longest ? Longest : wait;
        lock (gate)
        {
            timer?.Dispose();
            timer = time.CreateTimer(_ => onDue(), null, wait, Timeout.InfiniteTimeSpan);
        }
    }

    public void Cancel()
    {
        lock (gate)
        {
            timer?.Dispose();
            timer = null;
        }
    }

    public void Dispose() => Cancel();
}
