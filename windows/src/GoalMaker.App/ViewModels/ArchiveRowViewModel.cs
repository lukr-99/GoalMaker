using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// A done task in the archive: its title, when it was done, its project's chip when it was a project
/// item, and ways to open it or reopen it.
/// </summary>
public sealed record ArchiveRowViewModel(string Title, string DoneText, IRelayCommand OpenCommand, IRelayCommand ReopenCommand, ProjectTagViewModel? Project = null)
{
    public bool HasProject => Project is not null;
}
