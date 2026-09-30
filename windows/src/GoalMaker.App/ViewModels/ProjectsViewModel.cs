using System.Collections.ObjectModel;
using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;
using GoalMaker.Core.Sync;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The Projects page (docs/projects.md, spec stories 43 to 50): the owner's projects and the board of
/// the one on show, Backlog to Done. An item is a task, so moving a card writes through
/// <see cref="TaskList"/> and the item turns up in Today when it has a day. The who-made-it switch
/// shows every item, only the owner's, or only Claude's. Moving an item to Done or taking it out of
/// the project can be undone for five seconds, as on the lists. Done items leave the board the
/// project's number of days after the planning day they were finished, or when archived by hand, and
/// Done counts them and lists them with a way back. Any column folds to a strip, remembered in settings.
/// </summary>
public sealed partial class ProjectsViewModel : ObservableObject
{
    private const string Never = "never";

    // The width a column needs to keep a card readable, and a folded strip's, margins included.
    private const double ColumnWidth = 210;
    private const double StripWidth = 48;
    private static readonly TimeSpan UndoFor = TimeSpan.FromSeconds(5);
    private static readonly int[] ArchiveDays = [7, 14, 30, 90];
    private readonly ProjectList projects;
    private readonly TaskList tasks;
    private readonly ISettingsStore settings;
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
    private string projectArchiveAfter = ProjectRules.DefaultArchiveAfterDays.ToString(CultureInfo.InvariantCulture);

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

    public ProjectsViewModel(
        ProjectList projects, TaskList tasks, ISettingsStore settings, IStrings strings, Action<string> openTask, Action<Action> runOnUi, TimeProvider time)
    {
        this.projects = projects;
        this.tasks = tasks;
        this.settings = settings;
        this.strings = strings;
        this.openTask = openTask;
        this.runOnUi = runOnUi;
        this.time = time;
        projects.Changed += (_, _) => runOnUi(Refresh);
        tasks.Changed += (_, _) => runOnUi(Refresh);
        var folded = settings.FoldedBoardColumns;
        Columns =
        [
            .. ProjectRules.Columns.Select(column =>
            {
                var title = strings.Get(ColumnKey(column));
                return new BoardColumnViewModel(
                    column, title, strings.Get("Projects.Fold", title), strings.Get("Projects.Unfold", title), folded.Contains(column), Folded);
            }),
        ];
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

    /// <summary>How long done items stay on the board: the usual numbers of days, never, and the project's own number if it is another.</summary>
    public ObservableCollection<ChoiceViewModel> ArchiveChoices { get; } = [];

    /// <summary>The narrowest the board gets before it scrolls sideways: a card's width per open column, a strip per folded one.</summary>
    public double BoardMinWidth => Columns.Sum(column => column.IsFolded ? StripWidth : ColumnWidth);

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

        var shown = chosen is null ? null : projects.Get(chosen);
        var today = PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);
        var days = shown?.ArchiveAfterDays;
        var items = chosen is null
            ? []
            : everyItem.Where(task => task.ProjectId == chosen && ProjectRules.Shows(MadeByFilter, task.MadeBy)).ToList();
        var onBoard = items
            .Where(task => ProjectRules.OnBoard(task.State, CompletedOn(task), days, task.BoardArchivedAt is not null, today))
            .ToList();
        foreach (var column in Columns)
        {
            column.Items.Clear();
            foreach (var item in ProjectRules.Order(onBoard.Where(task => task.BoardColumn == column.Column)))
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
                    () => RemoveFromProject(card),
                    item.State == TaskState.Done,
                    () => Archive(card)));
            }
        }

        RefreshArchived(items.Except(onBoard), days, today);

        if (!IsEditing)
        {
            ProjectName = shown?.Name ?? string.Empty;
            ProjectDescription = shown?.Description ?? string.Empty;
            ProjectRepository = shown?.RepositoryUrl ?? string.Empty;
            ProjectFolder = shown?.LocalFolder ?? string.Empty;
            ProjectStatus = shown?.Status ?? ProjectRules.Active;
            ShowArchiveChoices(shown is null ? ProjectRules.DefaultArchiveAfterDays : shown.ArchiveAfterDays);
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
        ShowArchiveChoices(ProjectRules.DefaultArchiveAfterDays);
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

        var days = int.TryParse(ProjectArchiveAfter, NumberStyles.None, CultureInfo.InvariantCulture, out var number) ? number : (int?)null;
        if (chosen is { } saved && projects.Get(saved) is { } project && project.ArchiveAfterDays != days)
        {
            projects.SetArchiveAfterDays(saved, days);
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

    // Archiving by hand can be taken back too, which puts the item back in Done.
    private void Archive(TaskItem item)
    {
        if (tasks.SetBoardArchived(item.Id, true))
        {
            ShowUndo(strings.Get("Projects.ArchivedItem", item.Title), () => tasks.SetBoardArchived(item.Id, false));
        }
    }

    // The items off the board, under Done. Taking the hand archive off brings one back when it is still
    // young enough for Done; an older one goes back to To do, open again.
    private void RefreshArchived(IEnumerable<TaskItem> archived, int? days, DateOnly today)
    {
        var done = Columns.Single(column => column.Column == ProjectRules.Done);
        done.Archived.Clear();
        foreach (var item in archived.OrderByDescending(task => task.CompletedAt, StringComparer.Ordinal).ThenBy(task => task.Id, StringComparer.Ordinal))
        {
            var id = item.Id;
            var completedOn = CompletedOn(item);
            var unarchive = item.BoardArchivedAt is not null && ProjectRules.OnBoard(item.State, completedOn, days, false, today);
            done.Archived.Add(new ArchivedItemViewModel(
                item.Title,
                completedOn is { } day ? strings.Get("Archive.DoneOn", day.ToString("d MMM", CultureInfo.CurrentCulture)) : string.Empty,
                strings.Get(unarchive ? "Projects.PutBack" : "Projects.Reopen"),
                new RelayCommand(() =>
                {
                    if (unarchive)
                    {
                        tasks.SetBoardArchived(id, false);
                    }
                    else
                    {
                        tasks.SetBoardColumn(id, ProjectRules.Todo);
                    }
                })));
        }

        done.ArchivedText = strings.Get("Projects.ArchivedCount", done.Archived.Count);
        done.ShowsArchived &= done.HasArchived;
    }

    // The planning day an item was finished on, by the owner's day start, as the lists count days.
    private DateOnly? CompletedOn(TaskItem item) =>
        item.State == TaskState.Done && item.CompletedAt is { } stamp && SyncRules.InstantOf(stamp) is { } instant
            ? PlanningDay.Of(TimeZoneInfo.ConvertTime(instant, time.LocalTimeZone).DateTime, settings.DayStartHour)
            : null;

    // The usual numbers of days and Never; a project with another number, set through the connector,
    // keeps it on the list, so the picker never loses it.
    private void ShowArchiveChoices(int? days)
    {
        ArchiveChoices.Clear();
        foreach (var number in (days is { } own ? ArchiveDays.Append(own) : ArchiveDays).Distinct().Order())
        {
            ArchiveChoices.Add(new ChoiceViewModel(
                number.ToString(CultureInfo.InvariantCulture),
                strings.Get(number == 1 ? "Projects.ArchiveDay" : "Projects.ArchiveDays", number)));
        }

        ArchiveChoices.Add(new ChoiceViewModel(Never, strings.Get("Projects.ArchiveNever")));
        ProjectArchiveAfter = days?.ToString(CultureInfo.InvariantCulture) ?? Never;
    }

    // A column folded or opened: every board remembers it, and the board's narrowest width follows.
    private void Folded(BoardColumnViewModel column)
    {
        settings.FoldedBoardColumns = [.. Columns.Where(candidate => candidate.IsFolded).Select(candidate => candidate.Column)];
        OnPropertyChanged(nameof(BoardMinWidth));
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
