namespace GoalMaker.App.ViewModels;

/// <summary>
/// A new project item as the board's quick line or the new item window gives it
/// (docs/projects.md): a title, its type, column and priority, and the rest only when the window was
/// asked for it.
/// </summary>
public sealed record ProjectItemDraft(string Title, string ItemType, string Column, string Priority)
{
    /// <summary>The notes, in light Markdown, as a task's notes are.</summary>
    public string Notes { get; init; } = string.Empty;

    /// <summary>A milestone of the item's own project, or none.</summary>
    public string? MilestoneId { get; init; }

    public DateOnly? PlannedDay { get; init; }

    public DateOnly? Deadline { get; init; }
}
