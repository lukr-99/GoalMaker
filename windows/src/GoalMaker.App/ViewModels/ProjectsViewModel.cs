using System.Collections.ObjectModel;
using System.Globalization;
using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;
using GoalMaker.Core.Sync;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The Projects page (docs/projects.md, spec stories 43 to 50): the owner's projects and the board of
/// the one on show, Backlog to Done and then Dropped, which holds every dropped item. An item is a task, so moving a card writes through
/// <see cref="TaskList"/> and the item turns up in Today when it has a day. The who-made-it switch
/// shows every item, only the owner's, or only Claude's. Moving an item to Done or taking it out of
/// the project can be undone for five seconds, as on the lists. An area and tag filter of its own
/// narrows the project list and the board the way it narrows the lists: an item without an area of its
/// own counts as being in its project's, and a project stays listed while its own area is the one
/// chosen (no tag chosen) or the filter keeps one of its items. Done items leave the board the
/// project's number of days after the planning day they were finished, or when archived by hand, and
/// Done counts them and lists them with a way back. Any column folds to a strip, remembered in settings;
/// Dropped starts folded.
/// A new item goes in from the quick line above the board (a title and its type), or from the new item
/// window, which the New item button, a column's plus and Ctrl+N open with every field.
/// </summary>
public sealed partial class ProjectsViewModel : ObservableObject
{
    private const string Never = "never";

    // The width a column needs to keep a card readable, and a folded strip's, margins included.
    private const double ColumnWidth = 210;
    private const double StripWidth = 48;
    private static readonly TimeSpan UndoFor = TimeSpan.FromSeconds(5);
    private static readonly int[] ArchiveDays = [7, 14, 30, 90];
    private static readonly IReadOnlySet<string> NoTags = new HashSet<string>();
    private readonly ProjectList projects;
    private readonly TaskList tasks;
    private readonly AreaList areas;
    private readonly TagList tags;
    private readonly ListFilterState filter = new();
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly Action<string> openTask;
    private readonly Action<ProjectItemFormViewModel> openItemWindow;
    private readonly Action<Action> runOnUi;
    private readonly TimeProvider time;
    private string? chosen;
    private Action? undo;
    private ITimer? undoTimer;

    // Whether the new item window stays open for the next item, as it was last left.
    private bool addAnother;

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
    private string undoText = string.Empty;

    [ObservableProperty]
    private bool hasUndo;

    [ObservableProperty]
    private string madeByFilter = ProjectRules.Everyone;

    [ObservableProperty]
    private bool isFilteredAway;

    [ObservableProperty]
    private IReadOnlyList<ChoiceViewModel> projectAreaChoices = [];

    [ObservableProperty]
    private ChoiceViewModel? projectArea;

    /// <param name="openItemWindow">Shows the new item window over the main window for the form given.</param>
    public ProjectsViewModel(
        ProjectList projects,
        TaskList tasks,
        AreaList areas,
        TagList tags,
        ISettingsStore settings,
        IStrings strings,
        Func<string, Brush?> areaBrush,
        Action<string> openTask,
        Action<Action> runOnUi,
        TimeProvider time,
        Action<ProjectItemFormViewModel> openItemWindow)
    {
        this.projects = projects;
        this.tasks = tasks;
        this.areas = areas;
        this.tags = tags;
        this.settings = settings;
        this.strings = strings;
        this.openTask = openTask;
        this.openItemWindow = openItemWindow;
        this.runOnUi = runOnUi;
        this.time = time;
        projects.Changed += (_, _) => runOnUi(Refresh);
        tasks.Changed += (_, _) => runOnUi(Refresh);
        areas.Changed += (_, _) => runOnUi(Refresh);
        tags.Changed += (_, _) => runOnUi(Refresh);
        filter.Changed += (_, _) => runOnUi(Refresh);
        Filters = new ListFiltersViewModel(areas, tags, filter, strings, areaBrush, runOnUi);
        var folded = settings.FoldedBoardColumns;
        Columns =
        [
            .. ProjectRules.BoardColumns.Select(column =>
            {
                var title = strings.Get(ColumnKey(column));
                return new BoardColumnViewModel(
                    column,
                    title,
                    strings.Get("Projects.Fold", title),
                    strings.Get("Projects.Unfold", title),
                    folded.Contains(column),
                    Folded,
                    column is ProjectRules.Done or ProjectRules.Dropped ? null : new BoardColumnAdd(strings.Get("Projects.AddTo", title), () => OpenItemWindow(column)));
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

    /// <summary>The area and tag pickers over the board.</summary>
    public ListFiltersViewModel Filters { get; }

    /// <summary>The owner's projects the filter keeps, active ones first.</summary>
    public ObservableCollection<ProjectRowViewModel> Projects { get; } = [];

    /// <summary>The columns of the project on show: the four an item is stored in, then Dropped.</summary>
    public IReadOnlyList<BoardColumnViewModel> Columns { get; }

    /// <summary>The kinds an item can be, for the picker beside the quick line and in the new item window.</summary>
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
        var everyItem = tasks.All();
        var narrowed = filter.Current;
        var links = tags.TagLinks();
        var byProject = everyItem.Where(task => task.ProjectId is not null).ToLookup(task => task.ProjectId!, StringComparer.Ordinal);
        var listed = all.Where(project => narrowed.KeepsProject(project.AreaId, byProject[project.Id], links)).ToList();
        chosen = listed.Any(project => project.Id == chosen) ? chosen : listed.FirstOrDefault()?.Id;

        Projects.Clear();
        foreach (var project in listed)
        {
            var id = project.Id;
            var own = everyItem.Where(task => task.ProjectId == id).ToList();
            int Waiting(string column) => own.Count(task => ProjectRules.ShownIn(task.State, task.BoardColumn) == column);
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
            : byProject[chosen]
                .Where(task => ProjectRules.Shows(MadeByFilter, task.MadeBy) && narrowed.Matches(task, links.GetValueOrDefault(task.Id) ?? NoTags, shown?.AreaId))
                .ToList();
        var onBoard = items
            .Where(task => ProjectRules.OnBoard(task.State, CompletedOn(task), days, task.BoardArchivedAt is not null, today))
            .ToList();
        foreach (var column in Columns)
        {
            column.Items.Clear();
            foreach (var item in ProjectRules.Order(onBoard.Where(task => ProjectRules.ShownIn(task.State, task.BoardColumn) == column.Column)))
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
            ShowAreaChoices(shown?.AreaId);
        }

        IsEmpty = all.Count == 0;
        IsFilteredAway = all.Count > 0 && listed.Count == 0;
        NotifyProjectShown();
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
        ShowAreaChoices(filter.Current.AreaId);
        IsEditing = true;
        NotifyProjectShown();
    }

    /// <summary>Opens the project on show for editing.</summary>
    [RelayCommand]
    public void Edit() => IsEditing = true;

    /// <summary>Saves what the editor says, as a new project or a change to the one on show.</summary>
    [RelayCommand]
    public void Save()
    {
        // The notes are not on this form, so an edit keeps the ones the project has.
        var draft = new ProjectDraft(ProjectName)
        {
            Description = ProjectDescription,
            AreaId = ProjectArea?.Id,
            Status = ProjectStatus,
            RepositoryUrl = ProjectRepository,
            LocalFolder = ProjectFolder,
            Notes = chosen is { } current ? projects.Get(current)?.Notes ?? string.Empty : string.Empty,
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

    /// <summary>Adds the quick line's item to the project on show: its title and type, in the column the type calls for.</summary>
    [RelayCommand(CanExecute = nameof(CanAddItem))]
    public void AddItem()
    {
        if (chosen is { } projectId && Add(projectId, new ProjectItemDraft(NewItemTitle, NewItemType, ProjectRules.ColumnFor(NewItemType), ProjectRules.Normal)))
        {
            NewItemTitle = string.Empty;
        }
    }

    private bool CanAddItem() => !string.IsNullOrWhiteSpace(NewItemTitle) && chosen is not null;

    /// <summary>
    /// Opens the new item window for the project on show, in <paramref name="column"/> when a column's
    /// plus opened it, or with the column following the type. What the quick line holds moves into
    /// the window, and leaves the line once it has gone in.
    /// </summary>
    [RelayCommand(CanExecute = nameof(HasProject))]
    public void OpenItemWindow(string? column)
    {
        if (chosen is not { } projectId || projects.Get(projectId) is not { } project)
        {
            return;
        }

        var carried = NewItemTitle;
        var choices = new ProjectItemChoices(
            ItemTypes,
            NewItemColumns,
            Priorities,
            [.. projects.MilestonesOf(projectId).Select(milestone => new ChoiceViewModel(milestone.Id, milestone.Name))]);
        var form = new ProjectItemFormViewModel(
            strings,
            project.Name,
            choices,
            new ProjectItemStart(carried.Trim(), NewItemType, column),
            addAnother,
            draft =>
            {
                if (!Add(projectId, draft))
                {
                    return false;
                }

                if (carried.Length > 0 && NewItemTitle == carried)
                {
                    NewItemTitle = string.Empty;
                }

                return true;
            });
        form.Finished += (_, _) => addAnother = form.AddAnother;
        openItemWindow(form);
    }

    // Every new item goes in here, from the quick line or the window: the task with its day and notes,
    // then its project fields, as the board's rules want them (docs/projects.md).
    private bool Add(string projectId, ProjectItemDraft draft)
    {
        var line = new ComposerDraft(draft.Title, draft.PlannedDay, null, [], null, null, false, false, null, null, []);
        if (tasks.Add(line, draft.Notes.Trim()) is not { } task)
        {
            return false;
        }

        tasks.SetProject(task.Id, projectId, draft.ItemType);
        tasks.SetBoardColumn(task.Id, draft.Column);
        tasks.SetPriority(task.Id, draft.Priority);
        if (draft.MilestoneId is { } milestone)
        {
            tasks.SetMilestone(task.Id, milestone);
        }

        if (draft.Deadline is { } deadline)
        {
            tasks.SetDeadline(task.Id, deadline);
        }

        Refresh();
        return true;
    }

    private void NotifyProjectShown()
    {
        OnPropertyChanged(nameof(HasProject));
        OnPropertyChanged(nameof(ShowsProject));
        OpenItemWindowCommand.NotifyCanExecuteChanged();
    }

    // Moving to Done can be taken back, to the column the item showed in: a dropped one goes back to Dropped.
    private void Move(TaskItem item, string column)
    {
        tasks.SetBoardColumn(item.Id, column);
        if (column == ProjectRules.Done && ProjectRules.ShownIn(item.State, item.BoardColumn) is { } from && from != ProjectRules.Done)
        {
            ShowUndo(strings.Get("Lists.Done", item.Title), () => tasks.SetBoardColumn(item.Id, from));
        }
    }

    // Taking an item out can be taken back: it returns with its type, column and milestone. A dropped
    // item stays dropped, since moving it to its stored column would open it again.
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
            if (item.BoardColumn is { } column && item.State != TaskState.Dropped)
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

    // No area and the areas not archived, and the project's own area even when it is archived, so an
    // edit never loses it.
    private void ShowAreaChoices(string? areaId)
    {
        ProjectAreaChoices =
        [
            new ChoiceViewModel(null, strings.Get("Task.NoArea")),
            .. areas.All()
                .Where(area => !area.Archived || area.Id == areaId)
                .Select(area => new ChoiceViewModel(area.Id, area.Emoji is { } emoji ? $"{emoji} {area.Name}" : area.Name)),
        ];
        ProjectArea = ProjectAreaChoices.FirstOrDefault(choice => choice.Id == areaId) ?? ProjectAreaChoices[0];
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
        ProjectRules.Dropped => "Projects.Dropped",
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
