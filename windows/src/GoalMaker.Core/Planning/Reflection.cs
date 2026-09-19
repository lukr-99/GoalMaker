namespace GoalMaker.Core.Planning;

/// <summary>One prompt a review asked and the answer written to it (docs/reviews.md).</summary>
public sealed record Reflection(string PromptId, string Answer);
