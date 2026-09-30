namespace GoalMaker.Core.Planning;

/// <summary>
/// A Tally category: a shipped default, whose <see cref="Id"/> is its key (coding, video, ...), or one
/// of the owner's own. <see cref="Color"/> is a name from the area palette.
/// </summary>
public sealed record TallyCategory(string Id, string Name, string Color, string? Emoji = null);
