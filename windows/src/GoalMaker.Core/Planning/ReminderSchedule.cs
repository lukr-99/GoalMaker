namespace GoalMaker.Core.Planning;

/// <summary>
/// What a device does with its reminders when it looks (docs/reminders.md, pinned by
/// contracts/vectors/reminders.json). Only one timer is armed at a time: when it goes off the device
/// shows what arrived since its last look and arms the <see cref="Next"/> one, so a device that was
/// asleep catches up and a reminder is shown once.
/// </summary>
public static class ReminderSchedule
{
    /// <summary>Every reminder that has a time, soonest first, ties settled by id so both devices agree.</summary>
    public static IReadOnlyList<ScheduledReminder> Resolve(
        IEnumerable<ReminderItem> reminders,
        IReadOnlyDictionary<string, TaskItem> tasks,
        QuietHours quietHours)
    {
        var scheduled = new List<ScheduledReminder>();
        foreach (var reminder in reminders)
        {
            if (tasks.TryGetValue(reminder.TaskId, out var task) && ReminderRules.Fires(reminder, task, quietHours) is { } at)
            {
                scheduled.Add(new ScheduledReminder(reminder.Id, task.Id, task.Title, at, reminder.Important));
            }
        }

        return [.. scheduled.OrderBy(item => item.At).ThenBy(item => item.Id, StringComparer.Ordinal)];
    }

    /// <summary>What to show now: the reminders that arrived after <paramref name="since"/>, the last look, up to <paramref name="now"/>.</summary>
    public static IReadOnlyList<ScheduledReminder> Due(
        IEnumerable<ReminderItem> reminders,
        IReadOnlyDictionary<string, TaskItem> tasks,
        QuietHours quietHours,
        DateTime since,
        DateTime now) =>
        [.. Resolve(reminders, tasks, quietHours).Where(item => item.At > since && item.At <= now)];

    /// <summary>The reminder to arm the next timer for, or null when nothing is waiting.</summary>
    public static ScheduledReminder? Next(
        IEnumerable<ReminderItem> reminders,
        IReadOnlyDictionary<string, TaskItem> tasks,
        QuietHours quietHours,
        DateTime now) =>
        Resolve(reminders, tasks, quietHours).FirstOrDefault(item => item.At > now);

    /// <summary>
    /// Which of the notifications on screen (<paramref name="shown"/>, by reminder id) have to go: their
    /// reminder was handled, snoozed, moved later or deleted, or its task finished, here or on the other device.
    /// </summary>
    public static IReadOnlyList<string> Stale(
        IEnumerable<string> shown,
        IEnumerable<ReminderItem> reminders,
        IReadOnlyDictionary<string, TaskItem> tasks,
        DateTime now)
    {
        var byId = reminders.ToDictionary(reminder => reminder.Id, StringComparer.Ordinal);
        return [.. shown.Where(id => !StillDue(id)).Order(StringComparer.Ordinal)];

        bool StillDue(string id) =>
            byId.TryGetValue(id, out var reminder)
            && tasks.TryGetValue(reminder.TaskId, out var task)
            && ReminderRules.Due(reminder, task) is { } due
            && due <= now;
    }
}
