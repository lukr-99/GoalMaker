namespace GoalMaker.App.ViewModels;

/// <summary>
/// What the new item window opens with: the title and type the board's quick line held, and the
/// column it was opened from, or null to let the column follow the type.
/// </summary>
public sealed record ProjectItemStart(string Title, string ItemType, string? Column);
