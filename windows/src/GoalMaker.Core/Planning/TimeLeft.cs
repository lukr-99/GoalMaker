namespace GoalMaker.Core.Planning;

/// <summary>How far a life goal's by date is: "10 years left", "Today", "Past its date".</summary>
public sealed record TimeLeft(TimeLeftUnit Unit, int Count);
