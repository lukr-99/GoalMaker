namespace GoalMaker.Core.Assistant;

/// <summary>One line of the quick chat's thread, as the assistant function takes it.</summary>
public sealed record AssistantMessage(AssistantRole Role, string Text);
