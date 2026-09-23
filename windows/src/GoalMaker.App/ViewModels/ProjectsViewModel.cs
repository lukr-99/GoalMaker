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
/// <see cref="TaskList"/> and the item turns up in Today when it has a day. The who-made-it switch
/// shows every item, only the owner's, or only Claude's. Moving an item to Done or taking it out of
/// the project can be undone for five seconds, as on the lists.
/// </summary>
public sealed partial class ProjectsViewModel : ObservableObject
{
    private static readonly TimeSpan UndoFor = TimeSpan.FromSeconds(5);
    private readonly ProjectList projects;
    private readonly TaskList tasks;
    private readonly IStrings strings;
    private readonly Action<string> openTask;
    private readonly Action<Action> runOnUi;
    private readonly TimeProvider time;
    private string? chosen;
    private Action? undo;
    private ITimer? undoTimer;

    // The new item's column follows its type, an idea starting in the backlog, until one is picked.
    private bool columnPicked;
    private bool columnFollowing;

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
    [NotifyPropertyChangedFor(nameof(ProjectStatusText))]
    private string projectStatus = ProjectRules.Active;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(ShowsProject))]
    private bool isEditing;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(AddItemCommand))]
    private string newItemTitle = string.Empty;

    [ObservableProperty]
    private string newItemType = ProjectRules.Task;

    [ObservableProperty]
    private string newItemColumn = ProjectRules.Todo;

    [ObservableProperty]
    private string newItemPriority = ProjectRules.Normal;

    [ObservableProperty]
    private string newItemNotes = string.Empty;

    [ObservableProperty]
    private string undoText = string.Empty;

    [ObservableProperty]
    private bool hasUndo;

    [ObservableProperty]
    private string madeByFilter = ProjectRules.Everyone;

    public ProjectsViewModel(ProjectList projects, TaskList tasks, IStrings strings, Action<string> openTask, Action<Action> runOnUi, TimeProvider time)
    {
        this.projects = projects;
        this.tasks = tasks;
        this.strings = strings;
        this.openTask = openTask;
        this.runOnUi = runOnUi;
        this.time = time;
        projects.Changed += (_, _) => runOnUi(Refresh);
        tasks.Changed += (_, _) => runOnUi(Refresh);
        Columns = [.. ProjectRules.Columns.Select(column => new BoardColumnViewModel(column, strings.Get(ColumnKey(column))))];
        ItemTypes =
        [
            .. new[] { ProjectRules.Task, ProjectRules.Idea, ProjectRules.Bug }
                .Select(kind => new ChoiceViewModel(kind, strings.Get(TypeKey(kind)))),
        ];
        NewItemColumns =
        [
            .. new[] { ProjectRules.Backlog, ProjectRules.Todo, ProjectRules.Doing }
                .Select(column => new ChoiceViewModel(column, strings.Get(ColumnKey(column)))),
        ];
        Priorities = [.. ProjectRules.Priorities.Select(priority => new ChoiceViewModel(priority, strings.Get(PriorityKey(priority))))];
        Statuses =
        [
            .. new[] { ProjectRules.Active, ProjectRules.Paused, ProjectRules.Finished }
                .Select(status => new ChoiceViewModel(status, strings.Get(StatusKey(status)))),
        ];
        MakerFilters = [.. ProjectRules.MakerFilters.Select(filter => new ChoiceViewModel(filter, strings.Get(MakerKey(filter))))];
        Refresh();
    }

    /// <summary>The owner's projects, active ones first.</summary>
    public ObservableCollection<ProjectRowViewModel> Projects { get; } = [];

    /// <summary>The four columns of the project on show.</summary>
    public IReadOnlyList<BoardColumnViewModel> Columns { get; }

    /// <summary>The kinds an item can be, for the picker beside the new item box.</summary>
    public IReadOnlyList<ChoiceViewModel> ItemTypes { get; }

    /// <summary>The columns a new item can start in; Done is not one of them.</summary>
    public IReadOnlyList<ChoiceViewModel> NewItemColumns { get; }

    /// <summary>How important a new item is, urgent to low.</summary>
    public IReadOnlyList<ChoiceViewModel> Priorities { get; }

    /// <summary>The statuses a project can have, for the editor.</summary>
    public IReadOnlyList<ChoiceViewModel> Statuses { get; }

    /// <summary>The status of the project on show, in the owner's words.</summary>
    public string ProjectStatusText => strings.Get(StatusKey(ProjectStatus));

    /// <summary>What the who-made-it switch can show: everyone's items, the owner's, or Claude's.</summary>
    public IReadOnlyList<ChoiceViewModel> MakerFilters { get; }

    /// <summary>Whether a project is on show, so the board and its boxes are worth drawing.</summary>
    public bool HasProject => chosen is not null;

    /// <summary>The card of the project on show: there is one, and the editor is not in its place.</summary>
    public bool ShowsProject => HasProject && !IsEditing;

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
                project.Status,
                id == chosen,
                Waiting(ProjectRules.Backlog),
                Waiting(ProjectRules.Todo),
                Waiting(ProjectRules.Doing),
                () => Select(id)));
        }

        var items = chosen is null
            ? []
            : everyItem.Where(task => task.ProjectId == chosen && ProjectRules.Shows(MadeByFilter, task.MadeBy)).ToList();
        foreach (var column in Columns)
        {
            column.Items.Clear();
            foreach (var item in ProjectRules.Order(items.Where(task => task.BoardColumn == column.Column)))
            {
                var id = item.Id;
                var card = item;
                column.Items.Add(new BoardItemViewModel(
                    id,
                    item.Title,
                    strings.Get(TypeKey(item.ItemType)),
                    item.ItemType,
                    strings.Get(PriorityKey(item.Priority)),
                    item.State == TaskState.Dropped,
                    item.PlannedDate?.ToString("d MMM", CultureInfo.CurrentCulture),
                    item.MadeBy == ProjectRules.Claude,
                    column => Move(card, column),
                    () => openTask(id),
                    () => RemoveFromProject(card)));
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
        OnPropertyChanged(nameof(ShowsProject));
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
        OnPropertyChanged(nameof(ShowsProject));
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

    /// <summary>Adds an item to the project on show, in the column and at the priority chosen, with its notes.</summary>
    [RelayCommand(CanExecute = nameof(CanAddItem))]
    public void AddItem()
    {
        if (chosen is not { } projectId || tasks.Add(NewItemTitle) is not { } task)
        {
            return;
        }

        tasks.SetProject(task.Id, projectId, NewItemType);
        tasks.SetBoardColumn(task.Id, NewItemColumn);
        tasks.SetPriority(task.Id, NewItemPriority);
        if (!string.IsNullOrWhiteSpace(NewItemNotes))
        {
            tasks.SetNotes(task.Id, NewItemNotes.Trim());
        }

        NewItemTitle = string.Empty;
        NewItemNotes = string.Empty;
        Refresh();
    }

    private bool CanAddItem() => !string.IsNullOrWhiteSpace(NewItemTitle) && chosen is not null;

    partial void OnNewItemTypeChanged(string value)
    {
        if (columnPicked)
        {
            return;
        }

        columnFollowing = true;
        NewItemColumn = ProjectRules.ColumnFor(value);
        columnFollowing = false;
    }

    partial void OnNewItemColumnChanged(string value) => columnPicked |= !columnFollowing;

    // Moving to Done can be taken back, to the column the item came from.
    private void Move(TaskItem item, string column)
    {
        tasks.SetBoardColumn(item.Id, column);
        if (column == ProjectRules.Done && item.BoardColumn is { } from && from != ProjectRules.Done)
        {
            ShowUndo(strings.Get("Lists.Done", item.Title), () => tasks.SetBoardColumn(item.Id, from));
        }
    }

    // Taking an item out can be taken back: it returns with its type, column and milestone.
    private void RemoveFromProject(TaskItem item)
    {
        if (item.ProjectId is not { } projectId)
        {
            return;
        }

        tasks.SetProject(item.Id, null);
        ShowUndo(strings.Get("Projects.Removed", item.Title), () =>
        {
            tasks.SetProject(item.Id, projectId, item.ItemType);
            if (item.BoardColumn is { } column)
            {
                tasks.SetBoardColumn(item.Id, column);
            }

            tasks.SetMilestone(item.Id, item.MilestoneId);
        });
    }

    [RelayCommand]
    private void Undo()
    {
        var action = undo;
        HideUndo();
        action?.Invoke();
    }

    private void ShowUndo(string text, Action action)
    {
        undoTimer?.Dispose();
        undo = action;
        UndoText = text;
        HasUndo = true;
        undoTimer = time.CreateTimer(_ => runOnUi(HideUndo), null, UndoFor, Timeout.InfiniteTimeSpan);
    }

    private void HideUndo()
    {
        undoTimer?.Dispose();
        undoTimer = null;
        undo = null;
        HasUndo = false;
    }

    partial void OnMadeByFilterChanged(string value) => Refresh();

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

    private static string MakerKey(string filter) => filter switch
    {
        ProjectRules.Owner => "Projects.MadeByOwner",
        ProjectRules.Claude => "Projects.MadeByClaude",
        _ => "Projects.MadeByAll",
    };

    private static string StatusKey(string status) => status switch
    {
        ProjectRules.Paused => "Projects.Paused",
        ProjectRules.Finished => "Projects.Finished",
        _ => "Projects.Active",
    };
}
