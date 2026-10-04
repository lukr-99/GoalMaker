namespace GoalMaker.Core.Planning;

/// <summary>The why reminder to show (docs/life-goals.md): the period it belongs to, by its first day, and the life goal it shows.</summary>
public sealed record WhyDue(DateOnly PeriodStart, string LifeGoalId);
