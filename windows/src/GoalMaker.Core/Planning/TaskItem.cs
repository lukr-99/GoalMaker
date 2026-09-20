using System.Globalization;

namespace GoalMaker.Core.Planning;

/// <summary>
/// A task as the lists, the detail view and the archive show it. <see cref="SeriesId"/> groups a
/// repeating task's occurrences (docs/repeating.md); <see cref="CompletedAt"/> is the server
/// timestamp of a done task; <see cref="GoalId"/> is the goal it serves (docs/goals.md).
/// </summary>
public sealed record TaskItem(
    string Id,
    string Title,
    TaskState State,
    bool TopPriority,
    string CreatedAt,
    DateOnly? PlannedDate = null,
    TimeOnly? PlannedTime = null,
    string? AreaId = null,
    string? Recurrence = null,
    bool Deleted = false,
    string? SeriesId = null,
    string Notes = "",
    DateOnly? Deadline = null,
    string? CompletedAt = null,
    string? GoalId = null,

    /// <summary>How often the task was moved from one planned day to another (docs/reviews.md).</summary>
    int MovedCount = 0)
{
    /// <summary>The day it was finished, by the server's timestamp, or null while it is not done.</summary>
    public DateOnly? CompletedDay =>
        State == TaskState.Done
        && CompletedAt is { Length: >= 10 } stamp
        && DateOnly.TryParseExact(stamp[..10], "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out var day)
            ? day
            : null;
}
