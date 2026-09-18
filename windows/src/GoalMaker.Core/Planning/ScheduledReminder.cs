namespace GoalMaker.Core.Planning;

/// <summary>A reminder resolved to the local time it arrives, with what the notification needs to show.</summary>
public sealed record ScheduledReminder(string Id, string TaskId, string TaskTitle, DateTime At, bool Important);
