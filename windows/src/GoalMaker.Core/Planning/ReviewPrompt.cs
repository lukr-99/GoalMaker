namespace GoalMaker.Core.Planning;

/// <summary>
/// One prompt from the library (contracts/content/prompts.json): its <see cref="Id"/> as reviews store
/// it, the <see cref="Category"/> it rotates in, the review kinds it suits, its <see cref="Text"/> with
/// <c>{period}</c> and <c>{subject}</c> still in it, and the <see cref="Trigger"/> that makes it worth
/// asking, if any.
/// </summary>
public sealed record ReviewPrompt(string Id, string Category, IReadOnlySet<string> Reviews, string Text, string? Trigger = null);
