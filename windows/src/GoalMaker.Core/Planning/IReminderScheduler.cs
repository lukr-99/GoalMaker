namespace GoalMaker.Core.Planning;

/// <summary>Arms the device's one reminder timer (docs/reminders.md). Implemented by the platform.</summary>
public interface IReminderScheduler
{
    /// <summary>Replaces whatever was armed with a timer for <paramref name="at"/>, a local time.</summary>
    void ArmAt(DateTime at);

    /// <summary>Takes the armed timer away, because nothing is waiting.</summary>
    void Cancel();
}
