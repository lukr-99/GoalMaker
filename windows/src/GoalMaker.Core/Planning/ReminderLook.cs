namespace GoalMaker.Core.Planning;

/// <summary>
/// What one look at the reminders found (docs/reminders.md): the task reminders to show, and the
/// planning day whose evening Plan tomorrow reminder to show, if it is due.
/// </summary>
public sealed record ReminderLook(IReadOnlyList<ScheduledReminder> Reminders, DateOnly? PlanTomorrow = null);
