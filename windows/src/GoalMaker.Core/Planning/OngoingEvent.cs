namespace GoalMaker.Core.Planning;

/// <summary>An event a day falls inside: which of its days it is (<see cref="DayOf"/>, from 1) and how many it has.</summary>
public sealed record OngoingEvent(EventItem Event, int DayOf, int Days);
