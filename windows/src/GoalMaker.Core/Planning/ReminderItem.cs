namespace GoalMaker.Core.Planning;

/// <summary>
/// A reminder as the device schedules it (docs/reminders.md). Either <see cref="FireAt"/> holds its
/// own time or <see cref="OffsetMinutes"/> counts back from its task's planned time; both are local,
/// so the adapter turns the stored instants into the device's zone first.
/// </summary>
public sealed record ReminderItem(
    string Id,
    string TaskId,
    ReminderState State,
    bool Important = false,
    DateTime? FireAt = null,
    int? OffsetMinutes = null,
    DateTime? SnoozedUntil = null,
    bool Deleted = false);
