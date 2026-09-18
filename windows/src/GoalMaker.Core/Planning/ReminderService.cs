using GoalMaker.Core.Settings;

namespace GoalMaker.Core.Planning;

/// <summary>
/// Keeps the device's reminders in step with the replica (docs/reminders.md): says what to show now,
/// arms the timer for the next one, says which notifications on screen went stale, and settles a
/// reminder the owner handled. The time of the last look stays in the settings, so each reminder is
/// shown once.
/// </summary>
public sealed class ReminderService
{
    private readonly ReminderList reminders;
    private readonly TaskList tasks;
    private readonly IReminderScheduler scheduler;
    private readonly ISettingsStore settings;
    private readonly TimeProvider time;

    public ReminderService(ReminderList reminders, TaskList tasks, IReminderScheduler scheduler, ISettingsStore settings, TimeProvider time)
    {
        this.reminders = reminders;
        this.tasks = tasks;
        this.scheduler = scheduler;
        this.settings = settings;
        this.time = time;
        reminders.Changed += (_, _) => Changed?.Invoke(this, EventArgs.Empty);
    }

    /// <summary>Raised after every change to the reminders table.</summary>
    public event EventHandler? Changed;

    private DateTime Now => time.GetLocalNow().DateTime;

    /// <summary>
    /// What arrived since the last look, including anything missed while the PC slept, with the timer
    /// armed for whatever comes next. A device that never looked starts from now.
    /// </summary>
    public IReadOnlyList<ScheduledReminder> CatchUp()
    {
        var moment = time.GetLocalNow();
        var now = moment.DateTime;
        var (all, byId) = Read();
        var since = settings.RemindedUntil is { } last ? TimeZoneInfo.ConvertTime(last, time.LocalTimeZone).DateTime : now;
        var due = ReminderSchedule.Due(all, byId, settings.QuietHours, since, now);
        settings.RemindedUntil = moment;
        Arm(ReminderSchedule.Next(all, byId, settings.QuietHours, now));
        return due;
    }

    /// <summary>Which of the notifications on screen (<paramref name="shown"/>, by reminder id) have to go.</summary>
    public IReadOnlyList<string> Stale(IReadOnlyCollection<string> shown)
    {
        if (shown.Count == 0)
        {
            return [];
        }

        var (all, byId) = Read();
        return ReminderSchedule.Stale(shown, all, byId, Now);
    }

    /// <summary>Every reminder that isn't deleted, for the lists that mark waiting ones.</summary>
    public IReadOnlyList<ReminderItem> All() => reminders.All();

    /// <summary>The reminders on one task, for the screen that edits them.</summary>
    public IReadOnlyList<ReminderItem> On(string taskId) => reminders.ForTask(taskId);

    /// <summary>A reminder at its own time, armed right away.</summary>
    public ReminderItem? AddAt(string taskId, DateTime at, bool important = false)
    {
        var added = reminders.AddAt(taskId, at, important);
        Rearm();
        return added;
    }

    /// <summary>A reminder <paramref name="minutes"/> before the task's planned time, armed right away.</summary>
    public ReminderItem? AddBefore(string taskId, int minutes, bool important = false)
    {
        var added = reminders.AddBefore(taskId, minutes, important);
        Rearm();
        return added;
    }

    /// <summary>Takes a reminder away for good.</summary>
    public void Remove(string reminderId)
    {
        reminders.Delete(reminderId);
        Rearm();
    }

    /// <summary>Done from a notification: the task is finished and the reminder never comes back.</summary>
    public void Done(string reminderId)
    {
        if (reminders.All().FirstOrDefault(reminder => reminder.Id == reminderId) is { } reminder)
        {
            tasks.SetDone(reminder.TaskId, true);
        }

        reminders.MarkDone(reminderId);
        Rearm();
    }

    /// <summary>Dismissed, or opened from the notification: it never comes back, here or on the other device.</summary>
    public void Dismiss(string reminderId)
    {
        reminders.Dismiss(reminderId);
        Rearm();
    }

    /// <summary>Comes back where <paramref name="option"/> says (docs/reminders.md).</summary>
    public void Snooze(string reminderId, Snooze option)
    {
        reminders.Snooze(reminderId, option.Target(Now, settings.DayStartHour));
        Rearm();
    }

    /// <summary>Arms the timer for the next reminder, after a sync, a settings change or a wake from sleep.</summary>
    public void Rearm()
    {
        var (all, byId) = Read();
        Arm(ReminderSchedule.Next(all, byId, settings.QuietHours, Now));
    }

    private (IReadOnlyList<ReminderItem> All, IReadOnlyDictionary<string, TaskItem> ById) Read() =>
        (reminders.All(), tasks.All().ToDictionary(task => task.Id));

    private void Arm(ScheduledReminder? next)
    {
        if (next is null)
        {
            scheduler.Cancel();
        }
        else
        {
            scheduler.ArmAt(next.At);
        }
    }
}
