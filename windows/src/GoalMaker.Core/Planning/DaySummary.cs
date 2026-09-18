namespace GoalMaker.Core.Planning;

/// <summary>"2 of 5 done": today's planned tasks that are done, of those open or done.</summary>
public sealed record DaySummary(int Done, int Total);
