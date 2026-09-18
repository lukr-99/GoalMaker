using System.Windows.Media;
using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>An area or a tag in the sidebar's filters: a click narrows the lists to it, or lets go when it is chosen.</summary>
public sealed record FilterOptionViewModel(string Id, string Label, Brush? Brush, bool IsSelected, IRelayCommand Command)
{
    public bool HasBrush => Brush is not null;
}
