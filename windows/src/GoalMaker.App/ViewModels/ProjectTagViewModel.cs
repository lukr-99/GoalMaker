using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The chip a project item wears in a task list (docs/lists.md): its project's name, an idea's or a
/// bug's icon as on the board, what a screen reader says for it, and the way to the project's board
/// where the page can go there.
/// </summary>
public sealed class ProjectTagViewModel
{
    public ProjectTagViewModel(ProjectItem project, string itemType, IStrings strings, Action<string>? open = null)
    {
        ProjectId = project.Id;
        Name = project.Name;
        ItemType = itemType;
        Label = strings.Get(
            itemType switch
            {
                ProjectRules.Idea => "Lists.ProjectIdea",
                ProjectRules.Bug => "Lists.ProjectBug",
                _ => "Lists.ProjectTask",
            },
            project.Name);
        OpenLabel = strings.Get("Lists.OpenProject", project.Name);
        CanOpen = open is not null;
        OpenCommand = new RelayCommand(() => open?.Invoke(project.Id), () => open is not null);
    }

    public string ProjectId { get; }

    public string Name { get; }

    /// <summary>task, idea or bug; an idea and a bug carry their own icon and color.</summary>
    public string ItemType { get; }

    /// <summary>"Project GoalMaker", "Idea for GoalMaker" or "Bug in GoalMaker", for screen readers.</summary>
    public string Label { get; }

    /// <summary>"Open the GoalMaker board", the chip's tooltip and what it does.</summary>
    public string OpenLabel { get; }

    public bool CanOpen { get; }

    public IRelayCommand OpenCommand { get; }

    /// <summary>
    /// The chip for <paramref name="task"/> when it is an item of one of <paramref name="projects"/>
    /// (the projects that are not deleted, by id); null for any other task, as the stats count it.
    /// </summary>
    public static ProjectTagViewModel? For(
        TaskItem task,
        IReadOnlyDictionary<string, ProjectItem> projects,
        IStrings strings,
        Action<string>? open = null) =>
        task.ProjectId is { } id && projects.GetValueOrDefault(id) is { Deleted: false } project
            ? new ProjectTagViewModel(project, task.ItemType, strings, open)
            : null;
}
