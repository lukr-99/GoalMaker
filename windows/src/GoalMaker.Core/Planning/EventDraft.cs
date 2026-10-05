namespace GoalMaker.Core.Planning;

/// <summary>What the owner types for a calendar event (docs/calendar.md).</summary>
public sealed record EventDraft(
    string Title,
    DateOnly StartsOn,
    DateOnly EndsOn,
    string? Notes = null,
    string? AreaId = null);
