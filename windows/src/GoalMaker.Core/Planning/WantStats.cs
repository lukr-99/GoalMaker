namespace GoalMaker.Core.Planning;

/// <summary>The stats block for wants: how many were bought and dropped, and the dropped prices added up.</summary>
public sealed record WantStats(int Bought, int Dropped, double NotSpent);
