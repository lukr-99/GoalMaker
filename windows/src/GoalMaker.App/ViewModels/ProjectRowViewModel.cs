using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>One project in the list beside the board.</summary>
public sealed class ProjectRowViewModel(string id, string name, string status, bool selected, Action open)
{
    public string Id { get; } = id;

    public string Name { get; } = name;

    /// <summary>Active, paused or done, in the owner's words.</summary>
    public string Status { get; } = status;

    public bool IsSelected { get; } = selected;

    public IRelayCommand OpenCommand { get; } = new RelayCommand(open);
}
