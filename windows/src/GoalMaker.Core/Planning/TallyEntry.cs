namespace GoalMaker.Core.Planning;

/// <summary>
/// One line of the PC's raw Tally log: a window that was in front from <see cref="Start"/> to
/// <see cref="End"/> (local time), and where its time went when it was recorded.
/// </summary>
public sealed record TallyEntry(DateTime Start, DateTime End, string App, string? Title, string Category, string? Project);
