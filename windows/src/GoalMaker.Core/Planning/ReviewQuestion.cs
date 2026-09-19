namespace GoalMaker.Core.Planning;

/// <summary>A prompt as a review asks it: the prompt's id, its text with everything filled in, and what it is about.</summary>
public sealed record ReviewQuestion(string PromptId, string Text, string? Subject = null);
