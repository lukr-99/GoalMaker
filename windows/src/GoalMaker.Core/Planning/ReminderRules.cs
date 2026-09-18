namespace GoalMaker.Core.Planning;

/// <summary>When a reminder fires (docs/reminders.md, contracts/vectors/reminders.json).</summary>
public static class ReminderRules
{
    /// <summary>
    /// The reminder's own time, before quiet hours. Null when it never fires: either side is
    /// deleted, the task is no longer open, it is already handled, or it has no time to fire at.
    /// </summary>
    public static DateTime? Due(ReminderItem reminder, TaskItem task)
    {
        if (reminder.Deleted || task.Deleted || task.State != TaskState.Open)
        {
            return null;
        }

        return reminder.State switch
        {
            ReminderState.Dismissed or ReminderState.Done => null,
            ReminderState.Snoozed => reminder.SnoozedUntil,
            _ => Pending(reminder, task),
        };
    }

    /// <summary>When the reminder actually arrives, with quiet hours applied (important ones pass).</summary>
    public static DateTime? Fires(ReminderItem reminder, TaskItem task, QuietHours quietHours) =>
        Due(reminder, task) is { } due ? quietHours.Release(due, reminder.Important) : null;

    private static DateTime? Pending(ReminderItem reminder, TaskItem task)
    {
        if (reminder.FireAt is { } fireAt)
        {
            return fireAt;
        }

        if (reminder.OffsetMinutes is not { } offset || task.PlannedDate is not { } date || task.PlannedTime is not { } time)
        {
            return null;
        }

        return date.ToDateTime(time).AddMinutes(offset);
    }
}
