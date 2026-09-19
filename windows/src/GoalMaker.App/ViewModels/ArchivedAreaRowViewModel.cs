using System.Windows.Media;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>An archived area in the manager: its name and color, and ways to bring it back or delete it.</summary>
public sealed class ArchivedAreaRowViewModel(AreaItem area, Brush? brush, Action<string> restore, Action<string> delete)
{
    public string Id { get; } = area.Id;

    public string Label { get; } = area.Emoji is { } emoji ? $"{emoji} {area.Name}" : area.Name;

    public Brush? Brush { get; } = brush;

    public IRelayCommand RestoreCommand { get; } = new RelayCommand(() => restore(area.Id));

    public IRelayCommand DeleteCommand { get; } = new RelayCommand(() => delete(area.Id));
}
