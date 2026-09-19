namespace GoalMaker.Core.Planning;

/// <summary>An amount logged by hand on a numeric goal ("+5 km"); a negative one corrects an earlier entry.</summary>
public sealed record GoalEntryItem(string Id, string GoalId, DateOnly Day, double Amount, bool Deleted = false);
