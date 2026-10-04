namespace GoalMaker.Core.Planning;

/// <summary>When a period's why reminder rings: the period it belongs to, by its first day, and the moment.</summary>
public sealed record WhyMoment(DateOnly PeriodStart, DateTime At);
