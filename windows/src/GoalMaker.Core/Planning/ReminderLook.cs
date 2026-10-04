namespace GoalMaker.Core.Planning;

/// <summary>
/// What one look at the reminders found (docs/reminders.md): the task reminders to show, and the
/// planning day whose evening Plan tomorrow reminder to show, if it is due, and the habits whose reminder
/// rang, each still left on its planning day, and the why reminder with the life goal it shows.
/// </summary>
public sealed record ReminderLook(
    IReadOnlyList<ScheduledReminder> Reminders,
    DateOnly? PlanTomorrow = null,
    DateOnly? WeeklyReview = null,
    DateOnly? MonthlyReview = null,
    WantsDue? Wants = null,
    IReadOnlyList<DueHabit>? Habits = null,
    WhyDue? Why = null);
