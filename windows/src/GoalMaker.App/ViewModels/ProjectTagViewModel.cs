using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The chip a project item wears in a task list (docs/lists.md): its project's name, an idea's or a
/// bug's icon as on the board, the item's id (GM-12) once the server has numbered it, what a screen
/// reader says for it, and the way to the project's board where the page can go there.
/// </summary>
public sealed class ProjectTagViewModel
{
    public ProjectTagViewModel(ProjectItem project, string itemType, IStrings strings, Action<string>? open = null, string? itemId = null)
    {
        ProjectId = project.Id;
        Name = project.Name;
        ItemType = itemType;
        ItemId = itemId ?? string.Empty;
        var label = strings.Get(
            itemType switch
            {
                ProjectRules.Idea => "Lists.ProjectIdea",
                ProjectRules.Bug => "Lists.ProjectBug",
                _ => "Lists.ProjectTask",
            },
            project.Name);
        Label = ItemId.Length > 0 ? strings.Get("Lists.ProjectWithId", label, ItemId) : label;
        OpenLabel = strings.Get("Lists.OpenProject", project.Name);
        CanOpen = open is not null;
        OpenCommand = new RelayCommand(() => open?.Invoke(project.Id), () => open is not null);
    }

    public string ProjectId { get; }

    public string Name { get; }

    /// <summary>task, idea or bug; an idea and a bug carry their own icon and color.</summary>
    public string ItemType { get; }

    /// <summary>GM-12, or #12 in a project without a key; empty until the server has numbered the item.</summary>
    public string ItemId { get; }

    public bool HasItemId => ItemId.Length > 0;

    /// <summary>"Project GoalMaker", "Idea for GoalMaker" or "Bug in GoalMaker, GM-12", for screen readers.</summary>
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
            ? new ProjectTagViewModel(project, task.ItemType, strings, open, ProjectRules.ItemIdOf(task, project))
            : null;
}
