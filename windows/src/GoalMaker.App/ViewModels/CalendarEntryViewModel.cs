using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One line of what a day holds: a planned task, a deadline or a repeat (docs/calendar.md), with its
/// project's chip when it is a project item and its done box when it can be ticked off there.
/// </summary>
public sealed class CalendarEntryViewModel(
    string id,
    string title,
    string label,
    string time,
    bool done,
    Action open,
    ProjectTagViewModel? project = null,
    Action<bool>? setDone = null)
{
    /// <summary>The task's id, which a drag onto another day carries.</summary>
    public string Id { get; } = id;

    public string Title { get; } = title;

    /// <summary>Planned, due or repeats, in the owner's words.</summary>
    public string Label { get; } = label;

    public string Time { get; } = time;

    public bool HasTime { get; } = time.Length > 0;

    public bool Done { get; } = done;

    public IRelayCommand OpenCommand { get; } = new RelayCommand(open);

    public ProjectTagViewModel? Project { get; } = project;

    public bool HasProject { get; } = project is not null;

    /// <summary>A planned task or a deadline can be ticked off on the open day; a repeat has no row to tick yet.</summary>
    public bool CanTick { get; } = setDone is not null;

    /// <summary>Ticks the task off on the open day, or opens it again (docs/calendar.md).</summary>
    public IRelayCommand ToggleDoneCommand { get; } = new RelayCommand(() => setDone?.Invoke(!done));
}
