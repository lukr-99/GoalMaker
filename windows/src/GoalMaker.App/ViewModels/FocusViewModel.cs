using System.Collections.ObjectModel;
using System.Globalization;
using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The Focus Board (docs/focus.md): a manually-ordered watchlist of open tasks. Items are reordered with
/// Up/Down, completed or removed from the board without deleting. An area and tag filter of its own narrows
/// the board and the add panel, the add panel searches the open tasks as you type, and a typed line becomes
/// a task straight onto the board, with the composer's shortcuts read from it (docs/composer.md).
/// </summary>
public sealed partial class FocusViewModel : ObservableObject
{
    private readonly TaskList tasks;
    private readonly AreaList areas;
    private readonly TagList tags;
    private readonly ProjectList projects;
    private readonly ISettingsStore settings;
    private readonly TimeProvider time;
    private readonly IStrings strings;
    private readonly Func<string, Brush?> areaBrush;
    private readonly ListFilterState filter = new();

    [ObservableProperty]
    private ObservableCollection<FocusRowViewModel> items = [];

    [ObservableProperty]
    private bool isEmpty = true;

    /// <summary>Whether the board has tasks but the filter hides them all.</summary>
    [ObservableProperty]
    private bool isFilteredEmpty;

    /// <summary>How many tasks are on the board, and how many the filter shows: "5 on the board", "2 of 5 shown".</summary>
    [ObservableProperty]
    private string countText = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsAddClosed))]
    private bool isAddOpen;

    public bool IsAddClosed => !IsAddOpen;

    [ObservableProperty]
    private ObservableCollection<FocusAddRowViewModel> candidates = [];

    [ObservableProperty]
    private bool isCandidatesEmpty;

    /// <summary>Whether the add panel is empty with nothing searched or filtered: every open task is on the board.</summary>
    [ObservableProperty]
    private bool isAllOnBoard;

    /// <summary>Whether the add panel is empty for what is searched or filtered.</summary>
    [ObservableProperty]
    private bool isNoMatch;

    /// <summary>Whether there is no open task at all, on the board or off it, so the add panel has nothing to offer.</summary>
    [ObservableProperty]
    private bool hasNoOpenTasks;

    [ObservableProperty]
    private string quickAddTitle = string.Empty;

    /// <summary>What is typed in the add panel's search box.</summary>
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasQuery))]
    [NotifyPropertyChangedFor(nameof(CreateText))]
    [NotifyCanExecuteChangedFor(nameof(CreateFromQueryCommand))]
    private string query = string.Empty;

    public bool HasQuery => Query.Trim().Length > 0;

    /// <summary>The add panel's first row with something typed: make it a new task on the board.</summary>
    public string CreateText => strings.Get("Focus.Create", Query.Trim());

    public FocusViewModel(
        TaskList tasks,
        AreaList areas,
        TagList tags,
        ProjectList projects,
        ISettingsStore settings,
        IStrings strings,
        Func<string, Brush?> areaBrush,
        TimeProvider time,
        Action<Action> runOnUi)
    {
        this.tasks = tasks;
        this.areas = areas;
        this.tags = tags;
        this.projects = projects;
        this.settings = settings;
        this.time = time;
        this.strings = strings;
        this.areaBrush = areaBrush;
        Filters = new ListFiltersViewModel(areas, tags, filter, strings, areaBrush, runOnUi, projects);
        tasks.Changed += (_, _) => runOnUi(Refresh);
        areas.Changed += (_, _) => runOnUi(Refresh);
        tags.Changed += (_, _) => runOnUi(Refresh);
        projects.Changed += (_, _) => runOnUi(Refresh);
        filter.Changed += (_, _) => runOnUi(Refresh);
        Refresh();
    }

    /// <summary>The board's section label, in capitals as the design spec's section labels are.</summary>
    public string BoardLabel => strings.Get("Focus.OnBoard").ToUpper(CultureInfo.CurrentUICulture);

    /// <summary>The add panel's section label.</summary>
    public string AddLabel => strings.Get("Focus.AddTitle").ToUpper(CultureInfo.CurrentUICulture);

    /// <summary>The area and tag pickers the board and the add panel share.</summary>
    public ListFiltersViewModel Filters { get; }

    private void Refresh()
    {
        var all = tasks.All();
        var areaMap = areas.All().ToDictionary(a => a.Id);
        var board = FocusBoardRules.Board(all);
        var shown = Narrow(board);
        var today = Today();
        Items = new ObservableCollection<FocusRowViewModel>(
            shown.Select((t, index) => Row(t, areaMap, today, index + 1, shown.Count)));
        IsEmpty = board.Count == 0;
        IsFilteredEmpty = board.Count > 0 && shown.Count == 0;
        CountText = board.Count == 0
            ? string.Empty
            : shown.Count == board.Count
                ? strings.Get("Focus.Count", board.Count)
                : strings.Get("Focus.CountFiltered", shown.Count, board.Count);

        // The add panel sits beside the board on a wide window, so its rows stay current even while it is closed.
        RefreshCandidates(all, areaMap);
    }

    private DateOnly Today() => PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);

    private FocusRowViewModel Row(TaskItem task, Dictionary<string, AreaItem> areaMap, DateOnly today, int place, int count)
    {
        var area = areaMap.GetValueOrDefault(task.AreaId ?? "");
        return new FocusRowViewModel(task, area, today, place, count, strings.Get("Focus.Late"), area is null ? null : areaBrush(area.ColorId));
    }

    private FocusAddRowViewModel Candidate(TaskItem task, Dictionary<string, AreaItem> areaMap, DateOnly today)
    {
        var area = areaMap.GetValueOrDefault(task.AreaId ?? "");
        return new FocusAddRowViewModel(task, area, today, strings.Get("Focus.Late"), area is null ? null : areaBrush(area.ColorId));
    }

    private IReadOnlyList<TaskItem> Narrow(IReadOnlyList<TaskItem> found) =>
        filter.Current.IsEmpty
            ? found
            : filter.Current.Apply(found, tags.TagLinks(), projects.All().ToDictionary(project => project.Id, project => project.AreaId, StringComparer.Ordinal));

    private void RefreshCandidates(IReadOnlyList<TaskItem> all, Dictionary<string, AreaItem> areaMap)
    {
        var today = Today();
        var rows = Narrow(FocusBoardRules.Candidates(all, Query))
            .Select(t => Candidate(t, areaMap, today))
            .ToList();
        Candidates = new ObservableCollection<FocusAddRowViewModel>(rows);
        IsCandidatesEmpty = rows.Count == 0;
        var searching = HasQuery || !filter.Current.IsEmpty;
        IsAllOnBoard = IsCandidatesEmpty && !searching;
        IsNoMatch = IsCandidatesEmpty && searching;
        HasNoOpenTasks = !all.Any(task => !task.Deleted && task.State == TaskState.Open);
    }

    partial void OnQueryChanged(string value) => RefreshCandidates(tasks.All(), areas.All().ToDictionary(a => a.Id));

    [RelayCommand]
    private void Complete(FocusRowViewModel? row)
    {
        if (row is null) return;
        tasks.SetDone(row.Id, true);

        // Not done after all (a repeating task moved on, or nothing changed): the box must not stay ticked.
        if (!tasks.All().Any(task => task.Id == row.Id && task.State == TaskState.Done)) row.IsDone = false;
    }

    [RelayCommand]
    private void RemoveFromBoard(FocusRowViewModel? row)
    {
        if (row is null) return;
        tasks.SetFocusPosition(row.Id, null);
    }

    [RelayCommand]
    private void ShowAddDialog()
    {
        Query = string.Empty;
        IsAddOpen = true;
        RefreshCandidates(tasks.All(), areas.All().ToDictionary(a => a.Id));
    }

    [RelayCommand]
    private void CloseAddDialog()
    {
        IsAddOpen = false;
        Query = string.Empty;
    }

    /// <summary>
    /// Esc in the add panel's search box: clears what is typed first, then closes the panel where it covers
    /// the board (a narrow window).
    /// </summary>
    [RelayCommand]
    private void EscapeSearch()
    {
        if (Query.Length > 0)
        {
            Query = string.Empty;
            return;
        }

        if (IsAddOpen) CloseAddDialog();
    }

    [RelayCommand]
    private void AddToBoard(FocusAddRowViewModel? candidate)
    {
        if (candidate is null) return;
        tasks.SetFocusPosition(candidate.Id, FocusBoardRules.AppendPosition(tasks.All()));
    }

    [RelayCommand]
    private void QuickAdd()
    {
        if (Create(QuickAddTitle)) QuickAddTitle = string.Empty;
    }

    /// <summary>Makes a task from what is typed in the add panel's search box, onto the board, and closes the panel.</summary>
    [RelayCommand(CanExecute = nameof(HasQuery))]
    private void CreateFromQuery()
    {
        if (Create(Query)) CloseAddDialog();
    }

    /// <summary>
    /// Makes a task from <paramref name="line"/> at the end of the board, with the composer's shortcuts read
    /// from it, so "@School #forms friday" sets the area, a tag and the day. False when there is nothing to save.
    /// </summary>
    private bool Create(string line)
    {
        var draft = ComposerParser.Parse(line, time.GetLocalNow().DateTime, settings.DayStartHour);
        if (draft.Title.Trim().Length == 0 || draft.Command is not null) return false;
        var position = FocusBoardRules.AppendPosition(tasks.All());
        var item = tasks.Add(draft);
        if (item is null) return false;
        tasks.SetFocusPosition(item.Id, position);
        return true;
    }

    [RelayCommand]
    private void MoveUp(FocusRowViewModel? row)
    {
        if (row is null) return;
        var index = Items.IndexOf(row);
        if (index <= 0) return;
        Move(index, index - 1);
    }

    [RelayCommand]
    private void MoveDown(FocusRowViewModel? row)
    {
        if (row is null) return;
        var index = Items.IndexOf(row);
        if (index < 0 || index >= Items.Count - 1) return;
        Move(index, index + 1);
    }

    /// <summary>
    /// Moves a row to a place in the board as shown. While the filter hides part of the board, the task lands
    /// next to its shown neighbours, so the hidden tasks keep their places.
    /// </summary>
    private void Move(int fromIndex, int toIndex)
    {
        var shown = Items.ToList();
        var row = shown[fromIndex];
        shown.RemoveAt(fromIndex);
        var before = toIndex > 0 ? shown[toIndex - 1].FocusPosition : null;
        var after = toIndex < shown.Count ? shown[toIndex].FocusPosition : null;
        tasks.SetFocusPosition(row.Id, FocusBoardRules.Between(before, after));
    }
}
