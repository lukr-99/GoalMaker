namespace GoalMaker.Core.Planning;

/// <summary>Where a reminder stands (the server's reminders.state).</summary>
public enum ReminderState
{
    Pending,
    Snoozed,
    Dismissed,
    Done,
}
