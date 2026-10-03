using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One line of what a day holds: a planned task, a deadline or a repeat (docs/calendar.md), with its
/// project's chip when it is a project item.
/// </summary>
public sealed class CalendarEntryViewModel(string id, string title, string label, string time, bool done, Action open, ProjectTagViewModel? project = null)
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
}
