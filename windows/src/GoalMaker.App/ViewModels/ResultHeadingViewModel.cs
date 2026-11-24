using CommunityToolkit.Mvvm.Input;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// A heading in the search results: a project, with how many of its items were found and a way to its
/// board, or a column inside it with its count (docs/projects.md, "Searching items").
/// </summary>
public sealed class ResultHeadingViewModel(string key, string title, int count, bool isProject, Action? open = null)
{
    /// <summary>The project's id, or the project's id and the column's.</summary>
    public string Key { get; } = key;

    public string Title { get; } = title;

    public int Count { get; } = count;

    /// <summary>A project's heading, rather than a column's under it.</summary>
    public bool IsProject { get; } = isProject;

    public bool IsColumn => !IsProject;

    /// <summary>Shows the project's board.</summary>
    public IRelayCommand OpenCommand { get; } = new RelayCommand(() => open?.Invoke(), () => open is not null);
}
