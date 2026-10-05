namespace GoalMaker.Core.Planning;

/// <summary>
/// The piece of an event's bar one week row of the calendar draws (contracts/vectors/calendar.json,
/// 'bars'): from column <see cref="From"/> to <see cref="To"/> (0 is Monday), on <see cref="Lane"/>,
/// and whether the event goes on <see cref="Before"/> the row or <see cref="After"/> it.
/// </summary>
public sealed record EventBar(EventItem Event, int From, int To, int Lane, bool Before, bool After);
