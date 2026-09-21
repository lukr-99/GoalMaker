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
    private readonly Action? openPlan;
    private readonly ReminderService? reminders;
    private readonly TagList? tags;
    private readonly ListFilterState? filter;
    private readonly Action<string>? openTask;
    private readonly GoalList? goals;
    private readonly Action? openGoals;
    private readonly HabitsViewModel? habitsPage;
    private readonly HabitList? habitList;
    private readonly Action? openHabits;
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
    private IReadOnlyList<GoalRowViewModel> weekGoals = [];

    [ObservableProperty]
    private string weekGoalsHeader = string.Empty;

    [ObservableProperty]
    private bool hasWeekGoals;

    /// <summary>Whether this week's goals are unfolded under Today; they start folded (design spec, Today).</summary>
    [ObservableProperty]
    private bool isWeekGoalsExpanded;

    [ObservableProperty]
    private IReadOnlyList<HabitRowViewModel> habits = [];

    [ObservableProperty]
    private string habitsHeader = string.Empty;

    [ObservableProperty]
    private bool hasHabits;

    [ObservableProperty]
    private string undoText = string.Empty;

    [ObservableProperty]
    private bool hasUndo;

    [ObservableProperty]
    private string filterText = string.Empty;

    [ObservableProperty]
    private bool hasFilter;
    private readonly Action? openMini;

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
        Action<Action> runOnUi,
        Action? openPlan = null,
        ReminderService? reminders = null,
        TagList? tags = null,
        ListFilterState? filter = null,
        Action<string>? openTask = null,
        ListFiltersViewModel? filters = null,
        GoalList? goals = null,
        Action? openGoals = null,
        HabitsViewModel? habitsPage = null,
        HabitList? habitList = null,
        Action? openHabits = null,
        Action? openMini = null)
    {
        this.openTask = openTask;
        this.goals = goals;
        this.openGoals = openGoals;
        this.habitsPage = habitsPage;
        this.habitList = habitList;
        this.openHabits = openHabits;
        this.openMini = openMini;
        Filters = filters;
        this.openPlan = openPlan;
        this.reminders = reminders;
        this.tags = tags;
        this.filter = filter;
        ClearFilterCommand = new RelayCommand(() => filter?.Clear());
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
        if (reminders is not null)
        {
            reminders.Changed += (_, _) => runOnUi(Refresh);
        }

        if (tags is not null)
        {
            tags.Changed += (_, _) => runOnUi(Refresh);
        }

        if (filter is not null)
        {
            filter.Changed += (_, _) => runOnUi(Refresh);
        }

        if (goals is not null && kind == ListKind.Today)
        {
            goals.Changed += (_, _) => runOnUi(Refresh);
        }

        if (habitList is not null && kind == ListKind.Today)
        {
            habitList.Changed += (_, _) => runOnUi(Refresh);
        }

        sync.StatusChanged += (_, status) => runOnUi(() => ShowSync(status));
        ShowSync(sync.Status);
        Refresh();
    }

    public ListKind Kind { get; }

    public string Title { get; }

    public string EmptyText { get; }

    public ComposerViewModel Composer { get; }

    /// <summary>The area and tag pickers above the list, shared by every list; null where there are none.</summary>
    public ListFiltersViewModel? Filters { get; }

    public bool HasFilters => Filters is not null;

    public ObservableCollection<ListSectionViewModel> Sections { get; } = [];

    /// <summary>Lets go of the area and tag filter, from the line under the title.</summary>
    public IRelayCommand ClearFilterCommand { get; }

    // "Showing Home · #errand" under the title while the lists are narrowed (docs/lists.md).
    private void ShowFilter(ListFilter narrowed, IReadOnlyDictionary<string, AreaItem> areaById)
    {
        var parts = new List<string>();
        if (narrowed.AreaId is { } areaId && areaById.GetValueOrDefault(areaId) is { } area)
        {
            parts.Add(area.Emoji is { } emoji ? $"{emoji} {area.Name}" : area.Name);
        }

        if (narrowed.TagId is { } tagId && tags?.All().FirstOrDefault(tag => tag.Id == tagId) is { } chosen)
        {
            parts.Add("#" + chosen.Name);
        }

        HasFilter = parts.Count > 0;
        FilterText = HasFilter ? strings.Get("Lists.Filtered", string.Join(" · ", parts)) : string.Empty;
    }

    // What a task's menu offers (docs/reminders.md): counting back from its time when it has one, an
    // hour from now, tomorrow morning, and taking away each reminder already set.
    private IReadOnlyList<ReminderChoice> ReminderChoices(TaskItem task, IReadOnlyList<ReminderItem> own)
    {
        if (reminders is not { } service)
        {
            return [];
        }

        var now = time.GetLocalNow().DateTime;
        var choices = new List<ReminderChoice>();
        if (task.PlannedDate is not null && task.PlannedTime is not null)
        {
            choices.Add(new(strings.Get("Reminder.WhenDue"), new RelayCommand(() => service.AddBefore(task.Id, 0))));
            choices.Add(new(strings.Get("Reminder.QuarterBefore"), new RelayCommand(() => service.AddBefore(task.Id, 15))));
            choices.Add(new(strings.Get("Reminder.HourBefore"), new RelayCommand(() => service.AddBefore(task.Id, 60))));
        }

        choices.Add(new(strings.Get("Reminder.InAnHour"), new RelayCommand(() => service.AddAt(task.Id, time.GetLocalNow().DateTime.AddHours(1)))));
        choices.Add(new(
            strings.Get("Reminder.TomorrowMorning"),
            new RelayCommand(() => service.AddAt(task.Id, Snooze.TomorrowMorning.Target(time.GetLocalNow().DateTime, settings.DayStartHour)))));
        foreach (var reminder in own.Where(reminder => reminder.State is ReminderState.Pending or ReminderState.Snoozed))
        {
            var when = reminder.State == ReminderState.Snoozed ? reminder.SnoozedUntil : reminder.FireAt;
            var label = (when, -(reminder.OffsetMinutes ?? 0)) switch
            {
                ({ } at, _) => strings.Get("Reminder.RemoveAt", at.ToString(at.Date == now.Date ? "t" : "g", CultureInfo.CurrentCulture)),
                (null, 0) => strings.Get("Reminder.RemoveWhenDue"),
                (null, var minutes) => strings.Get("Reminder.RemoveBefore", minutes),
            };
            choices.Add(new(label, new RelayCommand(() => service.Remove(reminder.Id))));
        }

        return choices;
    }

    /// <summary>Rebuilds the list; also called when the planning day or its start hour may have moved on.</summary>
    public void Refresh()
    {
        var today = PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);
        var areaById = areas.All().ToDictionary(area => area.Id, StringComparer.Ordinal);
        var narrowed = filter?.Current ?? ListFilter.None;
        var all = tasks.All();
        var lists = ListRules.Lists(narrowed.IsEmpty || tags is null ? all : narrowed.Apply(all, tags.TagLinks()), today);
        ShowFilter(narrowed, areaById);
        var remindersByTask = (reminders?.All() ?? []).ToLookup(reminder => reminder.TaskId, StringComparer.Ordinal);
        List<TaskRowViewModel> Rows(IEnumerable<TaskItem> items, bool showDay = false) =>
            [.. items.Select(item =>
            {
                var area = item.AreaId is { } id ? areaById.GetValueOrDefault(id) : null;
                var own = remindersByTask[item.Id].ToList();
                return new TaskRowViewModel(
                    item,
                    area,
                    area is null ? null : areaBrush(area.ColorId),
                    showDay,
                    Complete,
                    Delete,
                    own.Any(reminder => reminder.State is ReminderState.Pending or ReminderState.Snoozed),
                    ReminderChoices(item, own),
                    openTask is null ? null : row => openTask(row.Item.Id),
                    strings);
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

                ShowHabits();
                if (goals is not null)
                {
                    WeekGoals = GoalsViewModel.ThisWeek(goals, tasks, today, strings, habitList);
                    HasWeekGoals = WeekGoals.Count > 0;
                    WeekGoalsHeader = Upper(strings.Get("Goals.WeekCount", WeekGoals.Count(row => row.IsHit), WeekGoals.Count));
                }

                var date = today.ToString("dddd d MMMM", CultureInfo.CurrentCulture);
                var summary = lists.Summary.Total == 0 ? date : strings.Get("Lists.TodaySummary", date, lists.Summary.Done, lists.Summary.Total);
                var habitsLeft = Habits.Count(row => !row.IsDone);
                Subtitle = habitsLeft == 0 ? summary : strings.Get("Habits.Summary", summary, strings.Get("Habits.Left", habitsLeft));
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

    /// <summary>Only Today has a mini window, so only Today offers the button.</summary>
    public bool HasMini => openMini is not null;

    [RelayCommand]
    private void OpenPlan() => openPlan?.Invoke();

    /// <summary>Today on its own, small and out of the way (docs/mini-windows.md).</summary>
    [RelayCommand]
    private void OpenMini() => openMini?.Invoke();

    [RelayCommand]
    private void OpenGoals() => openGoals?.Invoke();

    [RelayCommand]
    private void OpenHabits() => openHabits?.Invoke();

    // Today's habits as a row of rings, with how many are left (design spec, Today).
    private void ShowHabits()
    {
        if (habitsPage is null || Kind != ListKind.Today)
        {
            return;
        }

        Habits = habitsPage.TodayRows();
        HasHabits = Habits.Count > 0;
        var left = Habits.Count(row => !row.IsDone);
        HabitsHeader = Upper(left == 0 ? strings.Get("Habits.TodayDone") : strings.Get("Habits.TodayLeft", left));
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
        // The reason lives in Settings, where there is room to say what to do about it (docs/problems.md).
        { State: SyncState.NeedsAttention } => strings.Get("Sync.NeedsAttention"),
        { LastSyncedAt: { } at } => strings.Get("Sync.SyncedAt", at.ToLocalTime().ToString("t", CultureInfo.CurrentCulture)),
        _ => string.Empty,
    };
}
