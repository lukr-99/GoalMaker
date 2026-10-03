using GoalMaker.Core.Settings;

namespace GoalMaker.Core.Planning;

/// <summary>
/// Keeps the device's reminders in step with the replica (docs/reminders.md): says what to show now,
/// arms the timer for the next one, says which notifications on screen went stale, and settles a
/// reminder the owner handled. The time of the last look stays in the settings, so each reminder is
/// shown once. The evening Plan tomorrow reminder shares the one timer: it rings at the time in the
/// settings unless the ritual already ran that planning day, and so do the habits that remind, on the days
/// they are still left (<see cref="HabitReminder"/>).
/// </summary>
public sealed class ReminderService
{
    private readonly ReminderList reminders;
    private readonly TaskList tasks;
    private readonly IReminderScheduler scheduler;
    private readonly ISettingsStore settings;
    private readonly TimeProvider time;
    private readonly RitualRunList? rituals;
    private readonly WantList? wants;
    private readonly HabitList? habits;

    public ReminderService(
        ReminderList reminders,
        TaskList tasks,
        IReminderScheduler scheduler,
        ISettingsStore settings,
        TimeProvider time,
        RitualRunList? rituals = null,
        WantList? wants = null,
        HabitList? habits = null)
    {
        this.wants = wants;
        this.habits = habits;
        this.reminders = reminders;
        this.tasks = tasks;
        this.scheduler = scheduler;
        this.settings = settings;
        this.time = time;
        this.rituals = rituals;
        reminders.Changed += (_, _) => Changed?.Invoke(this, EventArgs.Empty);
    }

    /// <summary>Raised after every change to the reminders table.</summary>
    public event EventHandler? Changed;

    private DateTime Now => time.GetLocalNow().DateTime;

    /// <summary>
    /// What arrived since the last look, including anything missed while the PC slept, with the timer
    /// armed for whatever comes next. A device that never looked starts from now.
    /// </summary>
    public ReminderLook CatchUp()
    {
        var moment = time.GetLocalNow();
        var now = moment.DateTime;
        var (all, byId) = Read();
        var since = settings.RemindedUntil is { } last ? TimeZoneInfo.ConvertTime(last, time.LocalTimeZone).DateTime : now;
        var due = ReminderSchedule.Due(all, byId, settings.QuietHours, since, now);
        var planDay = rituals is null ? null : RitualReminder.Due(settings.PlanTomorrowReminder, settings.DayStartHour, RanPlanTomorrow(), since, now);
        settings.RemindedUntil = moment;
        Arm(all, byId, now);
        var weekly = ReviewDue(RitualRunList.WeeklyReview, settings.WeeklyReviewReminder, since, now);
        var monthly = ReviewDue(RitualRunList.MonthlyReview, settings.MonthlyReviewReminder, since, now);
        var ready = wants is null ? null : WantReminder.Due(settings.WantsReadyReminder, settings.DayStartHour, wants.All(), since, now);
        var dueHabits = new List<DueHabit>();
        if (habits is not null)
        {
            var (checkins, pauses) = (habits.Checkins(), habits.Pauses());
            foreach (var habit in habits.All())
            {
                if (HabitReminder.Due(habit, Own(checkins, habit.Id), Own(pauses, habit.Id), settings.DayStartHour, since, now) is { } day)
                {
                    dueHabits.Add(new DueHabit(habit, day));
                }
            }
        }

        return new ReminderLook(due, planDay, weekly, monthly, ready, dueHabits);
    }

    /// <summary>Whether the reminder on screen for a habit on planning <paramref name="day"/> has to go: the habit is no longer left, or the day moved on.</summary>
    public bool HabitStale(string habitId, DateOnly day)
    {
        if (habits?.All().FirstOrDefault(habit => habit.Id == habitId) is not { } habit)
        {
            return true;
        }

        return HabitReminder.Stale(habit, day, Own(habits.Checkins(), habitId), Own(habits.Pauses(), habitId), settings.DayStartHour, Now);
    }

    /// <summary>Check in from a habit's toast: a check is met, a count gets one more (an amount opens the app).</summary>
    public void CheckInHabit(string habitId, DateOnly day)
    {
        habits?.CheckIn(habitId, day);
        Rearm();
    }

    /// <summary>Skip from a habit's toast: its period that holds <paramref name="day"/> neither counts nor breaks the streak.</summary>
    public void SkipHabit(string habitId, DateOnly day)
    {
        habits?.Skip(habitId, day);
        Rearm();
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

    /// <summary>Whether the Plan tomorrow reminder on screen for <paramref name="day"/> has to go: the ritual ran, or the day moved on.</summary>
    public bool PlanTomorrowStale(DateOnly day) => RitualReminder.Stale(day, settings.DayStartHour, RanPlanTomorrow(), Now);

    /// <summary>Whether the review reminder of <paramref name="ritual"/> on screen for <paramref name="day"/> has to go.</summary>
    public bool ReviewStale(string ritual, DateOnly day) => ReviewReminder.Stale(day, settings.DayStartHour, Ran(ritual), Now);

    /// <summary>Whether the wants toast naming <paramref name="shown"/> has to go: every want in it was decided, here or elsewhere.</summary>
    public bool WantsStale(IReadOnlyCollection<string> shown) => wants is null || WantReminder.Stale(shown, wants.All());

    /// <summary>The review was written or put off on planning <paramref name="day"/>: its reminder stays quiet that day everywhere.</summary>
    public void FinishReview(string ritual, DateOnly day, bool skipped = false)
    {
        rituals?.Record(ritual, day, skipped);
        Rearm();
    }

    /// <summary>The ritual ran to the end on planning <paramref name="day"/>, so its reminder stays quiet that day on every device.</summary>
    public void FinishPlanTomorrow(DateOnly day)
    {
        rituals?.Record(RitualRunList.PlanTomorrow, day);
        Rearm();
    }

    /// <summary>"Not today" on the Plan tomorrow reminder: quiet for the rest of planning <paramref name="day"/>, on every device.</summary>
    public void SkipPlanTomorrow(DateOnly day)
    {
        rituals?.Record(RitualRunList.PlanTomorrow, day, skipped: true);
        Rearm();
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
        Arm(all, byId, Now);
    }

    private (IReadOnlyList<ReminderItem> All, IReadOnlyDictionary<string, TaskItem> ById) Read() =>
        (reminders.All(), tasks.All().ToDictionary(task => task.Id));

    private IReadOnlySet<DateOnly> RanPlanTomorrow() => Ran(RitualRunList.PlanTomorrow);

    private IReadOnlySet<DateOnly> Ran(string ritual) => rituals?.Ran(ritual) ?? new HashSet<DateOnly>();

    private static List<HabitCheckin> Own(IReadOnlyList<HabitCheckin> checkins, string habitId) =>
        [.. checkins.Where(checkin => checkin.HabitId == habitId)];

    private static List<HabitPause> Own(IReadOnlyList<HabitPause> pauses, string habitId) =>
        [.. pauses.Where(pause => pause.HabitId == habitId)];

    private static string KindOf(string ritual) => ritual == RitualRunList.MonthlyReview ? ReviewRules.Monthly : ReviewRules.Weekly;

    private DateOnly? ReviewDue(string ritual, TimeOnly? time, DateTime since, DateTime now) => rituals is null
        ? null
        : ReviewReminder.Due(KindOf(ritual), time, settings.WeeklyReviewWeekday, settings.DayStartHour, Ran(ritual), since, now);

    private DateTime? ReviewNext(string ritual, TimeOnly? time, DateTime now) => rituals is null
        ? null
        : ReviewReminder.Next(KindOf(ritual), time, settings.WeeklyReviewWeekday, settings.DayStartHour, Ran(ritual), now);

    // One timer for whichever comes first: a task's reminder, the evening Plan tomorrow reminder, a review
    // reminder, or the wants that become ready.
    private void Arm(IReadOnlyList<ReminderItem> all, IReadOnlyDictionary<string, TaskItem> byId, DateTime now)
    {
        var task = ReminderSchedule.Next(all, byId, settings.QuietHours, now)?.At;
        var ritual = rituals is null ? null : RitualReminder.Next(settings.PlanTomorrowReminder, settings.DayStartHour, RanPlanTomorrow(), now);
        var weekly = ReviewNext(RitualRunList.WeeklyReview, settings.WeeklyReviewReminder, now);
        var monthly = ReviewNext(RitualRunList.MonthlyReview, settings.MonthlyReviewReminder, now);
        var ready = wants is null ? null : WantReminder.Next(settings.WantsReadyReminder, settings.DayStartHour, wants.All(), now);
        DateTime? habit = null;
        if (habits is not null)
        {
            var (checkins, pauses) = (habits.Checkins(), habits.Pauses());
            habit = habits.All()
                .Select(one => HabitReminder.Next(one, Own(checkins, one.Id), Own(pauses, one.Id), settings.DayStartHour, now))
                .Where(moment => moment is not null)
                .Min();
        }

        var next = new[] { task, ritual, weekly, monthly, ready, habit }.Where(moment => moment is not null).Min();
        if (next is { } at)
        {
            scheduler.ArmAt(at);
        }
        else
        {
            scheduler.Cancel();
        }
    }
}
