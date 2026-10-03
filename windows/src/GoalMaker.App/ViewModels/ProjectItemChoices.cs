namespace GoalMaker.App.ViewModels;

/// <summary>
/// The lines of the new item window's pickers, the board's own: the types, the columns a new item can
/// start in, the priorities, and the milestones of the item's project.
/// </summary>
public sealed record ProjectItemChoices(
    IReadOnlyList<ChoiceViewModel> ItemTypes,
    IReadOnlyList<ChoiceViewModel> Columns,
    IReadOnlyList<ChoiceViewModel> Priorities,
    IReadOnlyList<ChoiceViewModel> Milestones);
