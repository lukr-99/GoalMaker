using System.Collections.ObjectModel;
using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The Projects page (docs/projects.md, spec stories 43 to 50): the owner's projects and the board of
/// the one on show, Backlog to Done. An item is a task, so moving a card writes through
/// <see cref="TaskList"/> and the item turns up in Today when it has a day.
/// </summary>
public sealed partial class ProjectsViewModel : ObservableObject
{
    private readonly ProjectList projects;
    private readonly TaskList tasks;
    private readonly IStrings strings;
    private readonly Action<string> openTask;
    private string? chosen;

    [ObservableProperty]
    private bool isEmpty;

    [ObservableProperty]
    private string projectName = string.Empty;

    [ObservableProperty]
    private string projectDescription = string.Empty;

    [ObservableProperty]
    private string projectRepository = string.Empty;

    [ObservableProperty]
    private string projectFolder = string.Empty;

    [ObservableProperty]
    private string projectStatus = ProjectRules.Active;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsNotEditing))]
    private bool isEditing;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(AddItemCommand))]
    private string newItemTitle = string.Empty;

    [ObservableProperty]
    private string newItemType = ProjectRules.Task;

    public ProjectsViewModel(ProjectList projects, TaskList tasks, IStrings strings, Action<string> openTask, Action<Action> runOnUi)
    {
        this.projects = projects;
        this.tasks = tasks;
        this.strings = strings;
        this.openTask = openTask;
        projects.Changed += (_, _) => runOnUi(Refresh);
        tasks.Changed += (_, _) => runOnUi(Refresh);
        Columns = [.. ProjectRules.Columns.Select(column => new BoardColumnViewModel(column, strings.Get(ColumnKey(column))))];
        ItemTypes =
        [
            .. new[] { ProjectRules.Task, ProjectRules.Idea, ProjectRules.Bug }
                .Select(kind => new ChoiceViewModel(kind, strings.Get(TypeKey(kind)))),
        ];
        Statuses =
        [
            .. new[] { ProjectRules.Active, ProjectRules.Paused, ProjectRules.Finished }
                .Select(status => new ChoiceViewModel(status, strings.Get(StatusKey(status)))),
        ];
        Refresh();
    }

    /// <summary>The owner's projects, active ones first.</summary>
    public ObservableCollection<ProjectRowViewModel> Projects { get; } = [];

    /// <summary>The four columns of the project on show.</summary>
    public IReadOnlyList<BoardColumnViewModel> Columns { get; }

    /// <summary>The kinds an item can be, for the picker beside the new item box.</summary>
    public IReadOnlyList<ChoiceViewModel> ItemTypes { get; }

    /// <summary>The statuses a project can have, for the editor.</summary>
    public IReadOnlyList<ChoiceViewModel> Statuses { get; }

    /// <summary>Whether a project is on show, so the board and its boxes are worth drawing.</summary>
    public bool HasProject => chosen is not null;

    /// <summary>The card of the project on show, which gives way to the editor.</summary>
    public bool IsNotEditing => !IsEditing;

    public void Refresh()
    {
        var all = projects.All();
        chosen = all.Any(project => project.Id == chosen) ? chosen : all.FirstOrDefault()?.Id;

        var everyItem = tasks.All();
        Projects.Clear();
        foreach (var project in all)
        {
            var id = project.Id;
            var own = everyItem.Where(task => task.ProjectId == id).ToList();
            int Waiting(string column) => own.Count(task => task.BoardColumn == column);
            Projects.Add(new ProjectRowViewModel(
                id,
                project.Name,
                strings.Get(StatusKey(project.Status)),
                id == chosen,
                Waiting(ProjectRules.Backlog),
                Waiting(ProjectRules.Todo),
                Waiting(ProjectRules.Doing),
                () => Select(id)));
        }

        var items = chosen is null ? [] : everyItem.Where(task => task.ProjectId == chosen).ToList();
        foreach (var column in Columns)
        {
            column.Items.Clear();
            foreach (var item in ProjectRules.Order(items.Where(task => task.BoardColumn == column.Column)))
            {
                var id = item.Id;
                column.Items.Add(new BoardItemViewModel(
                    id,
                    item.Title,
                    strings.Get(TypeKey(item.ItemType)),
                    item.ItemType,
                    strings.Get(PriorityKey(item.Priority)),
                    item.State == TaskState.Dropped,
                    item.PlannedDate?.ToString("d MMM", CultureInfo.CurrentCulture),
                    column => tasks.SetBoardColumn(id, column),
                    () => openTask(id),
                    () => tasks.SetProject(id, null)));
            }
        }

        if (!IsEditing)
        {
            var project = chosen is null ? null : projects.Get(chosen);
            ProjectName = project?.Name ?? string.Empty;
            ProjectDescription = project?.Description ?? string.Empty;
            ProjectRepository = project?.RepositoryUrl ?? string.Empty;
            ProjectFolder = project?.LocalFolder ?? string.Empty;
            ProjectStatus = project?.Status ?? ProjectRules.Active;
        }

        IsEmpty = all.Count == 0;
        OnPropertyChanged(nameof(HasProject));
    }

    /// <summary>Shows a project's board.</summary>
    public void Select(string id)
    {
        chosen = id;
        IsEditing = false;
        Refresh();
    }

    /// <summary>Starts a new project; the editor's boxes are cleared for it.</summary>
    [RelayCommand]
    public void New()
    {
        chosen = null;
        ProjectName = string.Empty;
        ProjectDescription = string.Empty;
        ProjectRepository = string.Empty;
        ProjectFolder = string.Empty;
        ProjectStatus = ProjectRules.Active;
        IsEditing = true;
        OnPropertyChanged(nameof(HasProject));
    }

    /// <summary>Opens the project on show for editing.</summary>
    [RelayCommand]
    public void Edit() => IsEditing = true;

    /// <summary>Saves what the editor says, as a new project or a change to the one on show.</summary>
    [RelayCommand]
    public void Save()
    {
        var draft = new ProjectDraft(ProjectName)
        {
            Description = ProjectDescription,
            Status = ProjectStatus,
            RepositoryUrl = ProjectRepository,
            LocalFolder = ProjectFolder,
        };
        if (chosen is { } id)
        {
            projects.Update(id, draft);
        }
        else if (projects.Add(draft) is { } added)
        {
            chosen = added.Id;
        }

        IsEditing = false;
        Refresh();
    }

    [RelayCommand]
    public void Cancel()
    {
        IsEditing = false;
        Refresh();
    }

    /// <summary>Deletes the project on show; its items stay as plain tasks.</summary>
    [RelayCommand]
    public void Delete()
    {
        if (chosen is { } id)
        {
            projects.Delete(id);
            chosen = null;
            IsEditing = false;
            Refresh();
        }
    }

    /// <summary>Adds an item to the project on show; it lands in the column its type calls for.</summary>
    [RelayCommand(CanExecute = nameof(CanAddItem))]
    public void AddItem()
    {
        if (chosen is not { } projectId || tasks.Add(NewItemTitle) is not { } task)
        {
            return;
        }

        tasks.SetProject(task.Id, projectId, NewItemType);
        NewItemTitle = string.Empty;
        Refresh();
    }

    private bool CanAddItem() => !string.IsNullOrWhiteSpace(NewItemTitle) && chosen is not null;

    private static string ColumnKey(string column) => column switch
    {
        ProjectRules.Backlog => "Projects.Backlog",
        ProjectRules.Todo => "Projects.Todo",
        ProjectRules.Doing => "Projects.Doing",
        _ => "Projects.Done",
    };

    private static string TypeKey(string itemType) => itemType switch
    {
        ProjectRules.Idea => "Projects.Idea",
        ProjectRules.Bug => "Projects.Bug",
        _ => "Projects.Task",
    };

    private static string PriorityKey(string priority) => priority switch
    {
        ProjectRules.Urgent => "Projects.Urgent",
        ProjectRules.High => "Projects.High",
        ProjectRules.Low => "Projects.Low",
        _ => "Projects.Normal",
    };

    private static string StatusKey(string status) => status switch
    {
        ProjectRules.Paused => "Projects.Paused",
        ProjectRules.Finished => "Projects.Finished",
        _ => "Projects.Active",
    };
}
