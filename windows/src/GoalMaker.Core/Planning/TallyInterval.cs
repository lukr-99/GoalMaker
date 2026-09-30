namespace GoalMaker.Core.Planning;

/// <summary>A stretch of foreground time in local time, already sorted into a category and project.</summary>
public sealed record TallyInterval(DateTime Start, DateTime End, string Category, string? Project);
