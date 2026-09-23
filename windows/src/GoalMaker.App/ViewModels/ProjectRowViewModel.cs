using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One project in the list beside the board, with how much its three open columns hold, so the load
/// of every project reads at a glance without opening it.
/// </summary>
public sealed class ProjectRowViewModel(
    string id,
    string name,
    string status,
    string statusId,
    bool selected,
    int backlog,
    int todo,
    int doing,
    Action open)
{
    public string Id { get; } = id;

    public string Name { get; } = name;

    /// <summary>Active, paused or done, in the owner's words.</summary>
    public string Status { get; } = status;

    /// <summary>The same status as GoalMaker writes it down, which the row styles its mark and name by.</summary>
    public string StatusId { get; } = statusId;

    public bool IsSelected { get; } = selected;

    /// <summary>How many items wait in Backlog.</summary>
    public int Backlog { get; } = backlog;

    /// <summary>How many items wait in To do.</summary>
    public int Todo { get; } = todo;

    /// <summary>How many items are in Doing.</summary>
    public int Doing { get; } = doing;

    public IRelayCommand OpenCommand { get; } = new RelayCommand(open);
}
