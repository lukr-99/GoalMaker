using System.Windows.Media;

namespace GoalMaker.App.ViewModels;

/// <summary>One area's group on the All tasks page: the area's name with its color, and its tasks.</summary>
public sealed record AllTasksGroupViewModel(string Name, Brush? AreaBrush, IReadOnlyList<AllTasksRowViewModel> Rows)
{
    public bool HasBrush => AreaBrush is not null;
}
