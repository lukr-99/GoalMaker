namespace GoalMaker.Core.Planning;

/// <summary>
/// A calendar event (docs/calendar.md, ADR 0019): something that takes up days rather than gets done,
/// a trip, a holiday, a conference, from <see cref="StartsOn"/> to <see cref="EndsOn"/>, both included.
/// </summary>
public sealed record EventItem(
    string Id,
    string Title,
    DateOnly StartsOn,
    DateOnly EndsOn,
    string? Notes = null,
    string? AreaId = null,
    string MadeBy = ProjectRules.Owner,
    string CreatedAt = "",
    bool Deleted = false)
{
    /// <summary>How many days the event takes up, 1 for a one-day event.</summary>
    public int Days => EndsOn.DayNumber - StartsOn.DayNumber + 1;
}
