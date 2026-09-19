using System.Windows.Media;

namespace GoalMaker.App.ViewModels;

/// <summary>One line of an area or tag picker above the lists; <see cref="Id"/> is null for "all". Equal by value, so a picker keeps its choice when rebuilt.</summary>
public sealed record FilterChoiceViewModel(string? Id, string Label, Brush? Brush)
{
    public bool HasBrush => Brush is not null;
}
