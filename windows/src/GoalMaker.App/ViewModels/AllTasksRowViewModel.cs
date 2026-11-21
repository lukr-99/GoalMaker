using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// An open task on the All tasks page: its title, its date line (late ones marked), its project's chip when it
/// is a project item, and ways to open it or complete it.
/// </summary>
public sealed record AllTasksRowViewModel(
    string Title,
    string DateText,
    bool IsLate,
    IRelayCommand OpenCommand,
    IRelayCommand CompleteCommand,
    ProjectTagViewModel? Project = null)
{
    public bool HasDate => DateText.Length > 0;

    public bool HasProject => Project is not null;
}
