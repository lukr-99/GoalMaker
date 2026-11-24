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
/// A project may have a key, so its items read GM-12 (docs/projects.md, "Item ids"): the form suggests
/// one from a new project's name, a card shows its id once the server has numbered it, the card's menu
/// copies it, and the board's search finds an item by its words, GM-12 or #12.
/// </summary>
public sealed partial class ProjectsViewModel : ObservableObject
{
    private const string Never = "never";

    // The width a column needs to keep a card readable, and a folded strip's, margins included.
    private const double ColumnWidth = 210;
    private const double StripWidth = 48;
    private static readonly TimeSpan UndoFor = TimeSpan.FromSeconds(5);

    // How long the searches wait after a key before they look, so typing a word looks once (GM-121).
    private static readonly TimeSpan SearchPause = TimeSpan.FromMilliseconds(150);
    private const int MostResults = 30;
    private const string PinnedGroup = "pinned";
    private const string NoAreaGroup = "none";
    private const string RestingGroup = "resting";
    private const string ProjectsGroup = "projects";
    private const string ItemsGroup = "items";
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
    private readonly Action<string>? copyText;
    private readonly Func<string, Brush?> areaBrush;
    private readonly Dictionary<string, ProjectRowViewModel> rowsById = new(StringComparer.Ordinal);
    private readonly Dictionary<string, ProjectGroupViewModel> groupsByKey = new(StringComparer.Ordinal);
    private readonly Dictionary<string, ItemResultViewModel> resultsById = new(StringComparer.Ordinal);
    private readonly Dictionary<string, Brush?> brushes = new(StringComparer.Ordinal);
    private readonly HashSet<string> foldedGroups = new(StringComparer.Ordinal) { RestingGroup };

    // What the page shows, read from the replica once per change rather than once per keystroke (GM-121).
    private ProjectBoardIndex index = ProjectBoardIndex.Empty;
    private IReadOnlyList<AreaItem> areaList = [];
    private IReadOnlyList<ProjectItem> listed = [];
    private bool stale = true;
    private bool reloadPending;

    // How many changes came in, so a read that a newer change overtook is not taken.
    private long changes;

    // A read was taken since the last redraw, so the redraw brings the editor's boxes up to date too.
    private bool freshRead;
    private ITimer? reloadTimer;
    private ITimer? searchTimer;

    // The project whose fields the editor holds, so a redraw that keeps the project leaves the boxes alone.
    private string? editorHolds;
    private string? chosen;
    private Action? undo;
    private ITimer? undoTimer;

    // Whether the new item window stays open for the next item, as it was last left.
    private bool addAnother;

    // Whether the owner typed the key of the project being written, so a new name no longer suggests one.
    private bool keyTyped;
    private bool suggesting;

    [ObservableProperty]
    private bool isEmpty;

    [ObservableProperty]
    private string projectName = string.Empty;

    [ObservableProperty]
    private string projectDescription = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(ProjectPlace))]
    private string projectRepository = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(ProjectPlace))]
    private string projectFolder = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(KeyHint))]
    private string projectKey = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasKeyProblem))]
    private string keyProblem = string.Empty;

    [ObservableProperty]
    private string boardQuery = string.Empty;

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

    /// <summary>The quick search over the project list: a project's name or key, an item's words or its id.</summary>
    [ObservableProperty]
    private string paneQuery = string.Empty;

    /// <summary>Whether the project list is out: beside the board in a wide window, over it in a narrow one.</summary>
    [ObservableProperty]
    private bool isPaneOpen = true;

    [ObservableProperty]
    private string moreFoundText = string.Empty;

    [ObservableProperty]
    private bool isNothingFound;

    /// <param name="openItemWindow">Shows the new item window over the main window for the form given.</param>
    /// <param name="copyText">Puts text on the clipboard, for a card's Copy id.</param>
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
        Action<ProjectItemFormViewModel> openItemWindow,
        Action<string>? copyText = null)
    {
        this.copyText = copyText;
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
        this.areaBrush = areaBrush;
        projects.Changed += (_, _) => runOnUi(DataChanged);
        tasks.Changed += (_, _) => runOnUi(DataChanged);
        areas.Changed += (_, _) => runOnUi(DataChanged);
        tags.Changed += (_, _) => runOnUi(DataChanged);
        filter.Changed += (_, _) => runOnUi(Rebuild);
        Filters = new ListFiltersViewModel(areas, tags, filter, strings, areaBrush, runOnUi, projects);
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

    /// <summary>
    /// What the project list shows, top to bottom (GM-122): headings (<see cref="ProjectGroupViewModel"/>)
    /// and projects (<see cref="ProjectRowViewModel"/>), pinned ones first and the rest by area; while
    /// the quick search has text, the projects it finds and then the items (<see cref="ItemResultViewModel"/>).
    /// </summary>
    public ShownCollection<object> PaneEntries { get; } = [];

    /// <summary>Whether the project list lies over the board (a narrow window), so picking a project puts it away.</summary>
    public bool PaneOverlay { get; set; }

    /// <summary>The card last opened from the board, which the keyboard goes back to when the page shows again.</summary>
    public string? LastOpenedItem { get; private set; }

    /// <summary>Raised when the board should bring a card into view and give it the keyboard: a found item jumped to.</summary>
    public event EventHandler<BoardItemViewModel>? CardShown;

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

    /// <summary>Where the project on show lives: its repository and its folder, whichever it has.</summary>
    public string ProjectPlace => string.Join("   ", new[] { ProjectRepository, ProjectFolder }.Where(part => part.Trim().Length > 0));

    /// <summary>The status of the project on show, in the owner's words.</summary>
    public string ProjectStatusText => strings.Get(StatusKey(ProjectStatus));

    /// <summary>What the who-made-it switch can show: everyone's items, the owner's, or Claude's.</summary>
    public IReadOnlyList<ChoiceViewModel> MakerFilters { get; }

    /// <summary>Whether a project is on show, so the board and its boxes are worth drawing.</summary>
    public bool HasProject => chosen is not null;

    /// <summary>How the project's items read with the key in the form: "Items read GM-12".</summary>
    public string KeyHint => ProjectKey.Trim() is { Length: > 0 } key
        ? strings.Get("Projects.KeyHint", ProjectRules.FormatItemId(key.ToUpperInvariant(), 12))
        : strings.Get("Projects.KeyNone");

    /// <summary>The key in the form can't be saved: it is not one, or another project uses it.</summary>
    public bool HasKeyProblem => KeyProblem.Length > 0;

    /// <summary>The card of the project on show: there is one, and the editor is not in its place.</summary>
    public bool ShowsProject => HasProject && !IsEditing;

    /// <summary>Reads the projects and their items again and redraws the page.</summary>
    public void Refresh()
    {
        stale = true;
        Rebuild();
    }

    /// <summary>Shows a project's board, and remembers it as the latest one opened.</summary>
    public void Select(string id)
    {
        chosen = id;
        IsEditing = false;
        Remember(id);
        if (PaneOverlay)
        {
            IsPaneOpen = false;
        }

        Rebuild();
    }

    /// <summary>
    /// Takes the board to a found item: its project, with the card picked and in view. The who-made-it
    /// switch and the board's search let go when they hide it, and its column opens when folded. An
    /// item that is not on the board (it left Done, or the area or tag filter hides it) opens on its own page.
    /// </summary>
    public void JumpTo(string itemId)
    {
        if (tasks.All().FirstOrDefault(task => task.Id == itemId) is not { ProjectId: { } projectId } item)
        {
            return;
        }

        chosen = projectId;
        IsEditing = false;
        Remember(projectId);
        if (!ProjectRules.Shows(MadeByFilter, item.MadeBy))
        {
            MadeByFilter = ProjectRules.Everyone;
        }

        if (!index.Finds(ProjectSearch.Of(BoardQuery), item, inProject: true))
        {
            BoardQuery = string.Empty;
        }

        if (PaneOverlay)
        {
            IsPaneOpen = false;
        }

        Rebuild();
        var column = Columns.FirstOrDefault(candidate => candidate.Items.Any(card => card.Id == itemId));
        if (column?.Items.First(card => card.Id == itemId) is not { } found)
        {
            OpenItem(itemId);
            return;
        }

        if (column.IsFolded)
        {
            column.FoldCommand.Execute(null);
        }

        CardShown?.Invoke(this, found);
    }

    /// <summary>Opens an item on its own page; Back returns to the board with the card where it was.</summary>
    public void OpenItem(string id)
    {
        LastOpenedItem = id;
        openTask(id);
    }

    // A table changed. Changes come in bursts (an item added writes four times, a sync pull many), so
    // they are gathered and read once, as soon as the burst is over. The read happens off the UI thread,
    // where the timer calls back; the UI thread only draws what changed (GM-121).
    private void DataChanged()
    {
        stale = true;
        changes++;
        if (reloadPending)
        {
            return;
        }

        reloadPending = true;
        var seen = changes;
        var previous = index;
        reloadTimer?.Dispose();
        reloadTimer = time.CreateTimer(
            _ =>
            {
                var read = Read(previous);
                runOnUi(() =>
                {
                    // A change that came in meanwhile left the page stale, and the redraw reads it.
                    reloadPending = false;
                    if (changes == seen)
                    {
                        Take(read);
                    }

                    Rebuild();
                });
            },
            null,
            TimeSpan.Zero,
            Timeout.InfiniteTimeSpan);
    }

    // Everything the page shows, read from the replica. Null when the read failed (the app is closing,
    // say); the UI thread then reads again itself.
    private (ProjectBoardIndex Index, IReadOnlyList<AreaItem> Areas)? Read(ProjectBoardIndex previous)
    {
        try
        {
            return (ProjectBoardIndex.Build(projects.All(), tasks.All(), tags.TagLinks(), previous), areas.All());
        }
        catch (Exception error) when (error is InvalidOperationException or ObjectDisposedException or System.Data.Common.DbException)
        {
            return null;
        }
    }

    private void Take((ProjectBoardIndex Index, IReadOnlyList<AreaItem> Areas)? read)
    {
        if (read is { } fresh)
        {
            index = fresh.Index;
            areaList = fresh.Areas;
            brushes.Clear();
            stale = false;
            freshRead = true;
        }
    }

    // Redraws the page from what was last read, after reading again when something changed: the project
    // list, the board of the project on show and the quick search. Rows and cards that did not change
    // stay as they are (GM-121).
    private void Rebuild()
    {
        searchTimer?.Dispose();
        searchTimer = null;
        var reloaded = stale || freshRead;
        freshRead = false;
        if (stale)
        {
            Take(Read(index));
        }

        var narrowed = filter.Current;
        var links = index.TagLinks;
        listed = [.. index.Projects.Where(project => narrowed.KeepsProject(project.AreaId, index.ItemsOf(project.Id), links))];

        // A new project being written has no project on show; otherwise the one picked, the latest one
        // opened, or the first.
        if (!(IsEditing && chosen is null) && !listed.Any(project => project.Id == chosen))
        {
            var recent = settings.RecentProjects;
            chosen = listed.Where(project => recent.Contains(project.Id)).MinBy(project => IndexOf(recent, project.Id))?.Id ?? listed.FirstOrDefault()?.Id;
        }

        UpdateRows();

        var shown = index.Project(chosen);
        var today = PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);
        var days = shown?.ArchiveAfterDays;
        var search = ProjectSearch.Of(BoardQuery);
        var items = chosen is null
            ? []
            : index.ItemsOf(chosen)
                .Where(task => ProjectRules.Shows(MadeByFilter, task.MadeBy) && narrowed.Matches(task, index.TagsOf(task.Id), shown?.AreaId))
                .Where(task => index.Finds(search, task, inProject: true))
                .ToList();
        var onBoard = items
            .Where(task => ProjectRules.OnBoard(task.State, CompletedOn(task), days, task.BoardArchivedAt is not null, today))
            .ToList();
        var cards = Columns.SelectMany(column => column.Items).ToDictionary(card => card.Id, StringComparer.Ordinal);
        foreach (var column in Columns)
        {
            column.Items.SyncTo([.. ProjectRules.Order(onBoard.Where(task => ProjectRules.ShownIn(task.State, task.BoardColumn) == column.Column)).Select(item => Card(item, shown, cards))]);
        }

        RefreshArchived(items.Except(onBoard), days, today, shown);

        if (!IsEditing && (reloaded || editorHolds != chosen))
        {
            editorHolds = chosen;
            ProjectName = shown?.Name ?? string.Empty;
            ProjectDescription = shown?.Description ?? string.Empty;
            ProjectRepository = shown?.RepositoryUrl ?? string.Empty;
            ProjectFolder = shown?.LocalFolder ?? string.Empty;
            ShowKey(shown?.ItemKey ?? string.Empty);
            ProjectStatus = shown?.Status ?? ProjectRules.Active;
            ShowArchiveChoices(shown is null ? ProjectRules.DefaultArchiveAfterDays : shown.ArchiveAfterDays);
            ShowAreaChoices(shown?.AreaId);
        }

        IsEmpty = index.Projects.Count == 0;
        IsFilteredAway = index.Projects.Count > 0 && listed.Count == 0;
        UpdatePane();
        NotifyProjectShown();
    }

    // The card for an item: the one already drawn while the item and its id are the same, else a new one.
    private BoardItemViewModel Card(TaskItem item, ProjectItem? shown, Dictionary<string, BoardItemViewModel> drawn)
    {
        var itemId = ProjectRules.ItemIdOf(item, shown);
        if (drawn.TryGetValue(item.Id, out var card) && card.Source == item && card.ItemId == (itemId ?? string.Empty))
        {
            return card;
        }

        var id = item.Id;
        var day = item.PlannedDate?.ToString("d MMM", CultureInfo.CurrentCulture);
        var extra = string.Concat(day is null ? string.Empty : " · " + day, item.MadeBy == ProjectRules.Claude ? " · " + strings.Get("Projects.ByClaude") : string.Empty);
        return new BoardItemViewModel(
            id,
            item.Title,
            strings.Get(TypeKey(item.ItemType)),
            item.ItemType,
            strings.Get(PriorityKey(item.Priority)),
            item.State == TaskState.Dropped,
            day,
            item.MadeBy == ProjectRules.Claude,
            column => Move(item, column),
            () => OpenItem(id),
            () => RemoveFromProject(item),
            item.State == TaskState.Done,
            () => Archive(item),
            itemId,
            itemId is null ? item.Title : strings.Get("Projects.ItemWithId", itemId, item.Title),
            copyText)
        {
            Source = item,
            Extra = extra,
        };
    }

    // One row per project the filter keeps, kept from redraw to redraw and brought up to date.
    private void UpdateRows()
    {
        var pinned = settings.PinnedProjects;
        var rows = new List<ProjectRowViewModel>(listed.Count);
        foreach (var project in listed)
        {
            var id = project.Id;
            if (!rowsById.TryGetValue(id, out var row))
            {
                row = new ProjectRowViewModel(id, strings.Get("Projects.Pin"), strings.Get("Projects.Unpin"), () => Select(id), () => TogglePin(id), () => EditProject(id));
                rowsById[id] = row;
            }

            var load = index.LoadOf(id);
            row.Name = project.Name;
            row.Status = strings.Get(StatusKey(project.Status));
            row.StatusId = project.Status;
            row.IsSelected = id == chosen;
            row.IsPinned = pinned.Contains(id);
            row.Backlog = load.Backlog;
            row.Todo = load.Todo;
            row.Doing = load.Doing;
            row.Open = load.Open;
            row.LoadText = strings.Get("Projects.Load", load.Open, load.Doing);
            row.AreaBrush = BrushOf(project.AreaId);
            rows.Add(row);
        }

        foreach (var gone in rowsById.Keys.Except(listed.Select(project => project.Id)).ToList())
        {
            rowsById.Remove(gone);
        }

        Projects.SyncTo(rows);
    }

    // The project list. Without a quick search: pinned projects, then active ones by area in the areas'
    // order, those without an area, and paused and done ones last, folded away. Headings show only when
    // there is more than one group. With a search: the projects whose name or key it finds, the latest
    // opened first, then the items it finds in any project.
    private void UpdatePane()
    {
        var entries = new List<object>();
        var resultsShown = 0;
        var total = 0;
        if (PaneQuery.Trim().Length > 0)
        {
            var words = PaneQuery.Split(' ', StringSplitOptions.RemoveEmptyEntries).Select(ArchiveRules.Fold).ToList();
            var recent = settings.RecentProjects;
            var found = listed
                .Where(project => words.TrueForAll(word => ArchiveRules.Fold(project.Name).Contains(word, StringComparison.Ordinal))
                    || string.Equals(project.ItemKey, PaneQuery.Trim(), StringComparison.OrdinalIgnoreCase))
                .OrderBy(project => recent.Contains(project.Id) ? IndexOf(recent, project.Id) : int.MaxValue)
                .Select(project => rowsById[project.Id])
                .ToList();
            if (found.Count > 0)
            {
                entries.Add(Group(ProjectsGroup, strings.Get("Projects.Title"), found.Count, foldable: false));
                entries.AddRange(found);
            }

            var results = index.Search(ProjectSearch.Of(PaneQuery), MostResults, out total);
            if (results.Count > 0)
            {
                entries.Add(Group(ItemsGroup, strings.Get("Projects.ItemsFound"), total, foldable: false));
                entries.AddRange(results.Select(Result));
            }

            resultsShown = results.Count;
            IsNothingFound = found.Count == 0 && results.Count == 0;
            foreach (var gone in resultsById.Keys.Except(results.Select(item => item.Id)).ToList())
            {
                resultsById.Remove(gone);
            }
        }
        else
        {
            IsNothingFound = false;
            resultsById.Clear();
            var pinned = settings.PinnedProjects;
            var groups = new List<(string Key, string Title, List<ProjectRowViewModel> Rows)>
            {
                (PinnedGroup, strings.Get("Projects.Pinned"), [.. pinned.Where(rowsById.ContainsKey).Select(id => rowsById[id])]),
            };
            var rest = listed.Where(project => !pinned.Contains(project.Id)).ToList();
            static bool Resting(ProjectItem project) => project.Status is ProjectRules.Paused or ProjectRules.Finished;
            foreach (var area in areaList)
            {
                groups.Add((area.Id, area.Emoji is { } emoji ? $"{emoji} {area.Name}" : area.Name, [.. rest.Where(project => !Resting(project) && project.AreaId == area.Id).Select(project => rowsById[project.Id])]));
            }

            var known = areaList.Select(area => area.Id).ToHashSet(StringComparer.Ordinal);
            groups.Add((NoAreaGroup, strings.Get("Projects.NoArea"), [.. rest.Where(project => !Resting(project) && (project.AreaId is null || !known.Contains(project.AreaId))).Select(project => rowsById[project.Id])]));
            groups.Add((RestingGroup, strings.Get("Projects.Resting"), [.. rest.Where(Resting).Select(project => rowsById[project.Id])]));
            var filled = groups.Where(group => group.Rows.Count > 0).ToList();
            foreach (var (key, title, rows) in filled)
            {
                // A lone group needs no heading, unless it is the folded one.
                var heading = filled.Count > 1 || key == RestingGroup;
                if (heading)
                {
                    entries.Add(Group(key, title, rows.Count, foldable: key == RestingGroup));
                }

                if (!heading || !foldedGroups.Contains(key))
                {
                    entries.AddRange(rows);
                }
            }
        }

        MoreFoundText = total > resultsShown ? strings.Get("Projects.MoreFound", total - resultsShown) : string.Empty;
        PaneEntries.SyncTo(entries);
    }

    private ProjectGroupViewModel Group(string key, string title, int count, bool foldable)
    {
        if (!groupsByKey.TryGetValue(key, out var group))
        {
            group = new ProjectGroupViewModel(key, foldable ? FoldGroup : null);
            groupsByKey[key] = group;
        }

        group.Title = title;
        group.Count = count;
        group.IsFolded = foldedGroups.Contains(key);
        return group;
    }

    // A found item with where it is: the project and its column, or that it left the board.
    private ItemResultViewModel Result(TaskItem item)
    {
        var project = index.Project(item.ProjectId);
        var itemId = ProjectRules.ItemIdOf(item, project);
        var today = PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);
        var column = ProjectRules.ShownIn(item.State, item.BoardColumn) ?? ProjectRules.Backlog;
        var where = item.State == TaskState.Done && !ProjectRules.OnBoard(item.State, CompletedOn(item), project?.ArchiveAfterDays, item.BoardArchivedAt is not null, today)
            ? strings.Get("Projects.OffBoard")
            : strings.Get(ColumnKey(column));
        var place = strings.Get("Projects.ResultPlace", project?.Name ?? string.Empty, where);
        if (resultsById.TryGetValue(item.Id, out var kept) && kept.Source == item && kept.Place == place && kept.ItemId == (itemId ?? string.Empty))
        {
            return kept;
        }

        var id = item.Id;
        var title = itemId is null ? item.Title : strings.Get("Projects.ItemWithId", itemId, item.Title);
        var result = new ItemResultViewModel(item, itemId, place, strings.Get("Projects.ResultName", title, place), () => JumpTo(id));
        resultsById[id] = result;
        return result;
    }

    private void FoldGroup(ProjectGroupViewModel group)
    {
        if (!foldedGroups.Remove(group.Key))
        {
            foldedGroups.Add(group.Key);
        }

        UpdatePane();
    }

    private Brush? BrushOf(string? areaId)
    {
        if (areaId is null)
        {
            return null;
        }

        if (!brushes.TryGetValue(areaId, out var brush))
        {
            brush = areaList.FirstOrDefault(area => area.Id == areaId) is { } area ? areaBrush(area.ColorId) : null;
            brushes[areaId] = brush;
        }

        return brush;
    }

    // Pins a project to the top of the list on this PC, or lets it go.
    private void TogglePin(string id)
    {
        var pinned = settings.PinnedProjects;
        settings.PinnedProjects = pinned.Contains(id) ? [.. pinned.Where(other => other != id)] : [.. pinned, id];
        Rebuild();
    }

    private void EditProject(string id)
    {
        Select(id);
        Edit();
    }

    private void Remember(string id) => settings.RecentProjects = [id, .. settings.RecentProjects.Where(other => other != id)];

    private static int IndexOf(IReadOnlyList<string> ids, string id)
    {
        for (var i = 0; i < ids.Count; i++)
        {
            if (ids[i] == id)
            {
                return i;
            }
        }

        return int.MaxValue;
    }

    // A key in a search box: look a moment later, unless the box was emptied, which shows everything now.
    private void SearchSoon(string query)
    {
        searchTimer?.Dispose();
        searchTimer = null;
        if (query.Trim().Length == 0)
        {
            Rebuild();
            return;
        }

        searchTimer = time.CreateTimer(_ => runOnUi(Rebuild), null, SearchPause, Timeout.InfiniteTimeSpan);
    }

    /// <summary>Enter in the quick search: the first project it found, or else the first item.</summary>
    [RelayCommand]
    private void PickFirst()
    {
        if (searchTimer is not null)
        {
            Rebuild();
        }

        switch (PaneEntries.FirstOrDefault(entry => entry is ProjectRowViewModel or ItemResultViewModel))
        {
            case ProjectRowViewModel row:
                PaneQuery = string.Empty;
                row.OpenCommand.Execute(null);
                break;
            case ItemResultViewModel result:
                result.JumpCommand.Execute(null);
                break;
        }
    }

    /// <summary>Esc in the quick search: empties it.</summary>
    [RelayCommand]
    private void ClearPaneQuery() => PaneQuery = string.Empty;

    /// <summary>Puts the project list away, or brings it out.</summary>
    [RelayCommand]
    private void TogglePane() => IsPaneOpen = !IsPaneOpen;

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
        keyTyped = false;
        SuggestKey();
        NotifyProjectShown();
    }

    /// <summary>Opens the project on show for editing; its key stays as it is.</summary>
    [RelayCommand]
    public void Edit()
    {
        KeyProblem = string.Empty;
        IsEditing = true;
    }

    /// <summary>Saves what the editor says, as a new project or a change to the one on show.</summary>
    [RelayCommand]
    public void Save()
    {
        // The key is checked first, so a key that can't be saved keeps the form open with the reason.
        var key = ProjectKey.Trim().ToUpperInvariant();
        if (key.Length > 0 && !ProjectRules.IsItemKey(key))
        {
            KeyProblem = strings.Get("Projects.KeyNotValid");
            return;
        }

        if (key.Length > 0 && projects.IsKeyTaken(key, chosen))
        {
            KeyProblem = strings.Get("Projects.KeyTaken", key);
            return;
        }

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

        if (chosen is { } keyed && projects.Get(keyed) is { } withKey && (withKey.ItemKey ?? string.Empty) != key)
        {
            projects.SetItemKey(keyed, key);
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
    private void RefreshArchived(IEnumerable<TaskItem> archived, int? days, DateOnly today, ProjectItem? project)
    {
        var done = Columns.Single(column => column.Column == ProjectRules.Done);
        var drawn = done.Archived.Where(row => row.Source is not null).ToDictionary(row => row.Source!.Value.Item.Id, StringComparer.Ordinal);
        var rows = new List<ArchivedItemViewModel>();
        foreach (var item in archived.OrderByDescending(task => task.CompletedAt, StringComparer.Ordinal).ThenBy(task => task.Id, StringComparer.Ordinal))
        {
            var id = item.Id;
            var completedOn = CompletedOn(item);
            var unarchive = item.BoardArchivedAt is not null && ProjectRules.OnBoard(item.State, completedOn, days, false, today);
            var itemId = ProjectRules.ItemIdOf(item, project);
            if (drawn.TryGetValue(id, out var kept) && kept.Source == (item, unarchive) && kept.ItemId == itemId)
            {
                rows.Add(kept);
                continue;
            }

            rows.Add(new ArchivedItemViewModel(
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
                }),
                itemId)
            {
                Source = (item, unarchive),
            });
        }

        done.Archived.SyncTo(rows);
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

    partial void OnMadeByFilterChanged(string value) => Rebuild();

    partial void OnBoardQueryChanged(string value) => SearchSoon(value);

    partial void OnPaneQueryChanged(string value) => SearchSoon(value);

    // A new project's name suggests its key until the owner types one of their own.
    partial void OnProjectNameChanged(string value)
    {
        if (IsEditing && chosen is null && !keyTyped)
        {
            SuggestKey();
        }
    }

    partial void OnProjectKeyChanged(string value)
    {
        KeyProblem = string.Empty;
        keyTyped |= !suggesting;
    }

    private void SuggestKey() => ShowKey(ProjectRules.SuggestItemKey(ProjectName, projects.OtherKeys()) ?? string.Empty);

    private void ShowKey(string key)
    {
        suggesting = true;
        ProjectKey = key;
        suggesting = false;
        KeyProblem = string.Empty;
    }

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
