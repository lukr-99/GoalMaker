namespace GoalMaker.Core.Planning;

/// <summary>
/// One Tally sorting rule (docs/tally.md): the owner's own (with an <see cref="Id"/> and maybe a
/// project) or a shipped default. <see cref="Match"/> is app, title or folder; <see cref="Platform"/>
/// android, windows or any; <see cref="Category"/> a default's key or an owner category's id.
/// </summary>
public sealed record TallyRule(string Match, string Pattern, string Platform, string Category, string? Project = null, string? Id = null);
