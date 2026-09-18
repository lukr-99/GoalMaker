using System.Collections.ObjectModel;
using System.Globalization;
using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.App.Shell;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;
using GoalMaker.Core.Sync;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// Today, Tomorrow or the Inbox (docs/lists.md): its sections and rows, the composer, completing
/// and deleting with undo, and the sync status. Rebuilt whenever tasks, areas or the planning day change.
/// </summary>
public sealed partial class ListViewModel : ObservableObject
{
    private static readonly TimeSpan UndoFor = TimeSpan.FromSeconds(5);
    private readonly TaskList tasks;
    private readonly AreaList areas;
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly TimeProvider time;
    private readonly Func<string, Brush?> areaBrush;
    private readonly Func<bool> reduceMotion;
    private readonly TickSound tick;
    private readonly Action<Action> runOnUi;
    private Action? undo;
    private ITimer? undoTimer;
    private bool overdueExpanded;

    [ObservableProperty]
    private string subtitle = string.Empty;

    [ObservableProperty]
    private string syncText = string.Empty;

    [ObservableProperty]
    private bool isEmpty;

    [ObservableProperty]
    private string undoText = string.Empty;

    [ObservableProperty]
    private bool hasUndo;

    public ListViewModel(
        ListKind kind,
        TaskList tasks,
        AreaList areas,
        ComposerViewModel composer,
        SyncCoordinator sync,
        ISettingsStore settings,
        IStrings strings,
        TimeProvider time,
        Func<string, Brush?> areaBrush,
        Func<bool> reduceMotion,
        TickSound tick,
        Action<Action> runOnUi)
    {
        Kind = kind;
        this.tasks = tasks;
        this.areas = areas;
        this.settings = settings;
        this.strings = strings;
        this.time = time;
        this.areaBrush = areaBrush;
        this.reduceMotion = reduceMotion;
        this.tick = tick;
        this.runOnUi = runOnUi;
        Composer = composer;
        Title = strings.Get(kind switch
        {
            ListKind.Today => "Lists.Today",
            ListKind.Tomorrow => "Lists.Tomorrow",
            _ => "Lists.Inbox",
        });
        EmptyText = strings.Get(kind switch
        {
            ListKind.Today => "Lists.TodayEmpty",
            ListKind.Tomorrow => "Lists.TomorrowEmpty",
            _ => "Lists.InboxEmpty",
        });
        tasks.Changed += (_, _) => runOnUi(Refresh);
        areas.Changed += (_, _) => runOnUi(Refresh);
        sync.StatusChanged += (_, status) => runOnUi(() => ShowSync(status));
        ShowSync(sync.Status);
        Refresh();
    }

    public ListKind Kind { get; }

    public string Title { get; }

    public string EmptyText { get; }

    public ComposerViewModel Composer { get; }

    public ObservableCollection<ListSectionViewModel> Sections { get; } = [];

    /// <summary>Rebuilds the list; also called when the planning day or its start hour may have moved on.</summary>
    public void Refresh()
    {
        var today = PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);
        var lists = ListRules.Lists(tasks.All(), today);
        var areaById = areas.All().ToDictionary(area => area.Id, StringComparer.Ordinal);
        List<TaskRowViewModel> Rows(IEnumerable<TaskItem> items, bool showDay = false) =>
            [.. items.Select(item =>
            {
                var area = item.AreaId is { } id ? areaById.GetValueOrDefault(id) : null;
                return new TaskRowViewModel(item, area, area is null ? null : areaBrush(area.ColorId), showDay, Complete, Delete);
            })];

        Sections.Clear();
        switch (Kind)
        {
            case ListKind.Today:
                var sections = lists.TodaySections;
                var labelled = sections.Priorities.Count > 0 || sections.Scheduled.Count > 0;
                Add(strings.Get("Lists.Priorities"), Rows(sections.Priorities));
                Add(strings.Get("Lists.Scheduled"), Rows(sections.Scheduled));
                Add(labelled ? strings.Get("Lists.More") : string.Empty, Rows(sections.More));
                IsEmpty = sections.Priorities.Count + sections.Scheduled.Count + sections.More.Count == 0;
                if (sections.Overdue.Count > 0)
                {
                    Sections.Add(new ListSectionViewModel(
                        Upper(strings.Get("Lists.Overdue", sections.Overdue.Count)),
                        collapsible: true,
                        overdueExpanded,
                        Rows(sections.Overdue, showDay: true),
                        expanded => overdueExpanded = expanded));
                }

                var date = today.ToString("dddd d MMMM", CultureInfo.CurrentCulture);
                Subtitle = lists.Summary.Total == 0 ? date : strings.Get("Lists.TodaySummary", date, lists.Summary.Done, lists.Summary.Total);
                break;
            case ListKind.Tomorrow:
                Add(string.Empty, Rows(lists.Tomorrow));
                IsEmpty = lists.Tomorrow.Count == 0;
                Subtitle = today.AddDays(1).ToString("dddd d MMMM", CultureInfo.CurrentCulture);
                break;
            default:
                Add(string.Empty, Rows(lists.Inbox));
                IsEmpty = lists.Inbox.Count == 0;
                Subtitle = strings.Get("Lists.InboxCount", lists.Inbox.Count);
                break;
        }
    }

    [RelayCommand]
    private void Undo()
    {
        var action = undo;
        HideUndo();
        action?.Invoke();
    }

    private void Add(string header, List<TaskRowViewModel> rows)
    {
        if (rows.Count > 0)
        {
            Sections.Add(new ListSectionViewModel(Upper(header), collapsible: false, expanded: true, rows));
        }
    }

    // The check shows for a moment before the row leaves (docs/design/spec.md), unless motion is reduced.
    private async void Complete(TaskRowViewModel row)
    {
        if (settings.Appearance.CompletionSound)
        {
            tick.Play();
        }

        if (!reduceMotion())
        {
            await Task.Delay(TimeSpan.FromMilliseconds(400), time);
            if (!row.IsDone)
            {
                return;
            }
        }

        tasks.SetDone(row.Item.Id, true);
        ShowUndo(strings.Get("Lists.Done", row.Title), () => tasks.SetDone(row.Item.Id, false));
    }

    private void Delete(TaskRowViewModel row)
    {
        tasks.Delete(row.Item.Id);
        ShowUndo(strings.Get("Lists.Deleted", row.Title), () => tasks.Restore(row.Item.Id));
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

    private static string Upper(string text) => text.ToUpper(CultureInfo.CurrentUICulture);

    private void ShowSync(SyncStatus status) => SyncText = status switch
    {
        { State: SyncState.Syncing } => strings.Get("Sync.Syncing"),
        { State: SyncState.Offline } => strings.Get("Sync.Offline", status.PendingChanges),
        { State: SyncState.NeedsAttention } => strings.Get("Sync.NeedsAttention", status.Problem ?? string.Empty),
        { LastSyncedAt: { } at } => strings.Get("Sync.SyncedAt", at.ToLocalTime().ToString("t", CultureInfo.CurrentCulture)),
        _ => string.Empty,
    };
}
