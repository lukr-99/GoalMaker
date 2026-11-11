using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>One candidate task in the Add to Focus Board panel.</summary>
public sealed class FocusAddRowViewModel
{
    public string Id { get; }
    public string Title { get; }
    public string? AreaName { get; }

    public FocusAddRowViewModel(TaskItem task, AreaItem? area)
    {
        Id = task.Id;
        Title = task.Title;
        AreaName = area?.Name;
    }

    public string Subtitle => AreaName ?? string.Empty;
    public bool HasSubtitle => !string.IsNullOrEmpty(Subtitle);
}
