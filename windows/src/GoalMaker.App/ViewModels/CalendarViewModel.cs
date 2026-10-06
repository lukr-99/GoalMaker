using System.Collections.ObjectModel;
using System.Globalization;
using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The Calendar page (docs/calendar.md, spec story 68): a week or a month of planned tasks, deadlines
/// and reminders, with a day showing what it holds and a task opening from there. An area and tag
/// filter of its own narrows what the grid counts and the day lists, the same filter the lists use.
/// Events are drawn as bars across the days they take up, listed above the open day's tasks, and open
/// an editor over the page; a deleted one can be brought back for five seconds. The bottom bar adds a
/// task or an event to the picked day; Ctrl+click and Shift+click (Ctrl+Space and Shift+Space on a
/// cell) pick several days, and Esc or a plain click goes back to one.
/// </summary>
public sealed partial class CalendarViewModel : ObservableObject
{
    private readonly TaskList tasks;
    private readonly ReminderList reminders;
    private readonly TagList tags;
    private readonly ProjectList projects;
    private readonly ListFilterState filter = new();
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly TimeProvider time;
    private readonly Action<string> openTask;
    private readonly Action<string>? openProject;
    private static readonly TimeSpan UndoFor = TimeSpan.FromSeconds(5);
    private readonly HabitsViewModel? habitsPage;
    private readonly EventList? events;
    private readonly AreaList areas;
    private readonly Func<string, Brush?> areaBrush;
    private readonly Action<Action> runOnUi;
    private Action? undo;
    private ITimer? undoTimer;
    private DateOnly? anchor;
    private DateOnly? selected;

    // The days picked with Ctrl or Shift, in the order they were picked; empty while only one day is.
    private List<DateOnly> picks = [];

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsWeek))]
    [NotifyPropertyChangedFor(nameof(IsMonth))]
    private string kind = CalendarRules.Month;

    [ObservableProperty]
    private string period = string.Empty;

    [ObservableProperty]
    private string dayTitle = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasNoDay))]
    private bool hasDay;

    [ObservableProperty]
    private bool isDayEmpty;

    [ObservableProperty]
    private string dayReminders = string.Empty;

    [ObservableProperty]
    private bool hasReminders;

    /// <summary>The open day's habits, when it is today or gone by: what can still be checked in there.</summary>
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasDayHabits))]
    private IReadOnlyList<HabitRowViewModel> dayHabits = [];

    [ObservableProperty]
    private bool hasUndo;

    [ObservableProperty]
    private string undoText = string.Empty;

    /// <summary>Whether several days are picked, so Esc goes back to one.</summary>
    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(LeavePickingCommand))]
    private bool isPicking;

    public CalendarViewModel(
        TaskList tasks,
        ReminderList reminders,
        AreaList areas,
        TagList tags,
        ProjectList projects,
        ISettingsStore settings,
        IStrings strings,
        Func<string, Brush?> areaBrush,
        TimeProvider time,
        Action<string> openTask,
        Action<Action> runOnUi,
        Action<string>? openProject = null,
        HabitsViewModel? habitsPage = null,
        HabitList? habitList = null,
        EventList? events = null,
        ChatViewModel? chat = null)
    {
        this.events = events;
        this.areas = areas;
        this.areaBrush = areaBrush;
        this.runOnUi = runOnUi;
        Editor = events is null ? null : new EventEditorViewModel(events, areas, strings);
        if (events is not null)
        {
            events.Changed += (_, _) => runOnUi(Refresh);
            areas.Changed += (_, _) => runOnUi(Refresh);
            Editor!.Deleted += (_, item) => ShowUndo(strings.Get("Event.Deleted", item.Title), () => events.Restore(item.Id));
        }

        this.openProject = openProject;
        this.habitsPage = habitsPage;
        if (habitList is not null)
        {
            habitList.Changed += (_, _) => runOnUi(Refresh);
        }

        this.tasks = tasks;
        this.reminders = reminders;
        this.tags = tags;
        this.projects = projects;
        this.settings = settings;
        this.strings = strings;
        this.time = time;
        this.openTask = openTask;
        tasks.Changed += (_, _) => runOnUi(Refresh);
        reminders.Changed += (_, _) => runOnUi(Refresh);
        tags.Changed += (_, _) => runOnUi(Refresh);
        projects.Changed += (_, _) => runOnUi(Refresh);
        filter.Changed += (_, _) => runOnUi(Refresh);
        Filters = new ListFiltersViewModel(areas, tags, filter, strings, areaBrush, runOnUi);
        Weekdays = [.. Enumerable.Range(0, 7).Select(day => CultureInfo.CurrentCulture.DateTimeFormat.AbbreviatedDayNames[(day + 1) % 7])];
        Bar = new CalendarComposerViewModel(
            tasks,
            events,
            areas,
            tags,
            projects,
            settings,
            strings,
            time,
            areaBrush,
            PickedDays,
            ShowUndo,
            runOnUi,
            events is null ? null : (first, last, title) => Editor!.OpenNew(first, last, title),
            chat);
        Refresh();
    }

    /// <summary>The bottom bar, which adds to the picked days.</summary>
    public CalendarComposerViewModel Bar { get; }

    /// <summary>Which of the two views is on show, so its button reads as chosen.</summary>
    public bool IsWeek => Kind == CalendarRules.Week;

    public bool IsMonth => Kind == CalendarRules.Month;

    /// <summary>Whether no day is open, so the page asks the owner to pick one.</summary>
    public bool HasNoDay => !HasDay;

    /// <summary>The area and tag pickers over the grid.</summary>
    public ListFiltersViewModel Filters { get; }

    /// <summary>The weekday headings, Monday first.</summary>
    public IReadOnlyList<string> Weekdays { get; }

    /// <summary>The days of the grid, seven to a row.</summary>
    public ObservableCollection<CalendarCellViewModel> Cells { get; } = [];

    /// <summary>The grid's week rows: the same cells, with the event bars across them.</summary>
    public ObservableCollection<CalendarWeekViewModel> Weeks { get; } = [];

    /// <summary>The events of the day the owner picked, listed above its tasks.</summary>
    public ObservableCollection<CalendarEventViewModel> DayEvents { get; } = [];

    /// <summary>The event editor over the page; null where there are no events.</summary>
    public EventEditorViewModel? Editor { get; }

    /// <summary>What the day the owner picked holds.</summary>
    public ObservableCollection<CalendarEntryViewModel> DayEntries { get; } = [];

    /// <summary>
    /// What a reader says for a day's cell: "Saturday, 3 October 2026, today, 2 planned, 1 due", or that
    /// nothing is on it. The same parts, in the same order, as the phone's cell; tomorrow is named as today is.
    /// </summary>
    private string CellName(CalendarDay day, int eventCount, bool isToday, bool isTomorrow, bool isOpen, bool isPicked)
    {
        var parts = new List<string> { day.Day.ToString("D", CultureInfo.CurrentCulture) };
        if (isToday)
        {
            parts.Add(strings.Get("Calendar.CellToday"));
        }

        if (isTomorrow)
        {
            parts.Add(strings.Get("Calendar.CellTomorrow"));
        }

        if (isPicked)
        {
            parts.Add(strings.Get("Calendar.CellPicked"));
        }

        if (isOpen)
        {
            parts.Add(strings.Get("Calendar.CellOpen"));
        }

        void Count(int count, string key)
        {
            if (count > 0)
            {
                parts.Add(count == 1 ? strings.Get(key + "One") : strings.Get(key, count));
            }
        }

        Count(day.Planned.Count, "Calendar.CellPlanned");
        Count(day.Deadlines.Count, "Calendar.CellDue");
        Count(day.Repeats.Count, "Calendar.CellRepeats");
        Count(day.Reminders, "Calendar.CellReminders");
        Count(eventCount, "Calendar.CellEvents");
        if (day.Empty && eventCount == 0)
        {
            parts.Add(strings.Get("Calendar.CellEmpty"));
        }

        return string.Join(", ", parts);
    }

    public void Refresh()
    {
        var today = Today();
        var shown = anchor ?? today;
        var narrowed = filter.Current;
        var links = narrowed.IsEmpty ? null : tags.TagLinks();
        var projectAreas = narrowed.IsEmpty ? null : projects.All().ToDictionary(project => project.Id, project => project.AreaId, StringComparer.Ordinal);
        var start = CalendarRules.Start(Kind, shown);
        var end = CalendarRules.End(Kind, shown);
        var days = CalendarRules.Build(
            tasks.All(),
            reminders.All(),
            start,
            end,
            links is null ? null : task => narrowed.Keeps(task, links, projectAreas));
        var shownEvents = (events?.Between(start, end) ?? []).Where(item => EventRules.Keeps(narrowed, item)).ToList();
        var eventDays = EventRules.Days(shownEvents, start, end);

        Cells.Clear();
        foreach (var day in days)
        {
            var date = day.Day;
            var picked = picks.Contains(date);
            var isToday = date == today;
            var isTomorrow = date == today.AddDays(1);
            Cells.Add(new CalendarCellViewModel(
                date,
                date.Day.ToString(CultureInfo.CurrentCulture),
                day.Count,
                isToday,
                Kind == CalendarRules.Week || date.Month == shown.Month,
                date == selected,
                () => Open(date),
                CellName(day, eventDays[date].Count, isToday, isTomorrow, date == selected, picked),
                picked,
                () => Pick(date),
                () => PickRun(date),
                isTomorrow,
                isToday ? strings.Get("Calendar.TagToday") : isTomorrow ? strings.Get("Calendar.TagTomorrow") : null));
        }

        ShowWeeks(shownEvents, start, end);

        Period = Kind == CalendarRules.Month
            ? shown.ToString("MMMM yyyy", CultureInfo.CurrentCulture)
            : strings.Get(
                "Goals.Range",
                CalendarRules.Start(CalendarRules.Week, shown).ToString("d MMM", CultureInfo.CurrentCulture),
                CalendarRules.End(CalendarRules.Week, shown).ToString("d MMM", CultureInfo.CurrentCulture));

        var open = days.FirstOrDefault(day => day.Day == selected);
        DayEvents.Clear();
        IReadOnlyList<EventItem> openEvents = open is null ? [] : eventDays[open.Day];
        foreach (var item in openEvents)
        {
            DayEvents.Add(new CalendarEventViewModel(
                item.Id, item.Title, EventText.Days(item, strings), ColourOf(item), EventText.Name(item, strings), () => OpenEvent(item.Id)));
        }

        DayEntries.Clear();
        if (open is not null)
        {
            var projectById = projects.All().ToDictionary(project => project.Id, StringComparer.Ordinal);
            foreach (var (task, label) in Entries(open))
            {
                var id = task.Id;
                var tickable = label != "Calendar.Repeat" && task.State != TaskState.Dropped;
                DayEntries.Add(new CalendarEntryViewModel(
                    id,
                    task.Title,
                    strings.Get(label),
                    task.PlannedTime?.ToString("t", CultureInfo.CurrentCulture) ?? string.Empty,
                    task.State == TaskState.Done,
                    () => openTask(id),
                    ProjectTagViewModel.For(task, projectById, strings, openProject),
                    tickable ? done => SetDone(id, done, open.Day) : null));
            }
        }

        // A day gone by, or today, can still be checked in; a day to come can't (docs/calendar.md).
        DayHabits = open is not null && open.Day <= today && habitsPage is not null ? habitsPage.DayRows(open.Day, ShowUndo) : [];

        DayTitle = open is null ? string.Empty : open.Day.ToString("D", CultureInfo.CurrentCulture);
        HasDay = open is not null;
        IsDayEmpty = open is not null && open.Empty && DayEvents.Count == 0;
        HasReminders = open is { Reminders: > 0 };
        DayReminders = HasReminders ? strings.Get("Calendar.Reminders", open!.Reminders) : string.Empty;
        IsPicking = picks.Count > 1;
        Bar?.DaysChanged();
    }

    /// <summary>
    /// The days the bar adds to, in the order they were picked: the picked ones while several are, else
    /// the open day, else today.
    /// </summary>
    public IReadOnlyList<DateOnly> PickedDays() => picks.Count > 1 ? [.. picks] : [selected ?? Today()];

    /// <summary>Ctrl+click or Ctrl+Space on a cell: adds the day to the pick, or takes it out.</summary>
    public void Pick(DateOnly day)
    {
        var days = Current();
        if (!days.Remove(day))
        {
            days.Add(day);
        }

        Keep(days, days.Contains(day) ? day : null);
    }

    /// <summary>Shift+click or Shift+Space on a cell: adds the run of days from the last one picked to this one.</summary>
    public void PickRun(DateOnly day)
    {
        var days = Current();
        if (days.Count == 0)
        {
            Keep([day], day);
            return;
        }

        var from = days[^1];
        var step = day >= from ? 1 : -1;
        for (var next = from; next != day.AddDays(step); next = next.AddDays(step))
        {
            if (!days.Contains(next))
            {
                days.Add(next);
            }
        }

        Keep(days, day);
    }

    /// <summary>Esc: back to one day, the open one.</summary>
    [RelayCommand(CanExecute = nameof(IsPicking))]
    private void LeavePicking()
    {
        picks = [];
        Refresh();
    }

    // The days picked now: the pick, or the open day alone.
    private List<DateOnly> Current() => picks.Count > 1 ? [.. picks] : selected is { } open ? [open] : [];

    // Keeps a pick: several days stay picked with the one just touched open; one is just the open day.
    private void Keep(List<DateOnly> days, DateOnly? touched)
    {
        picks = days.Count > 1 ? days : [];
        selected = days.Count switch
        {
            0 => null,
            1 => days[0],
            _ => touched ?? days[^1],
        };
        Refresh();
    }

    /// <summary>Whether the open day lists habits to check in.</summary>
    public bool HasDayHabits => DayHabits.Count > 0;

    /// <summary>
    /// Ticks a task off on <paramref name="day"/>, or opens it again: a day gone by counts it as done on that
    /// day, so the stats and the archive put it there (docs/calendar.md).
    /// </summary>
    public void SetDone(string taskId, bool done, DateOnly day)
    {
        if (done)
        {
            tasks.FinishOn(taskId, day, Today(), time.LocalTimeZone);
        }
        else
        {
            tasks.SetDone(taskId, false);
        }
    }

    /// <summary>Shows the week or the month.</summary>
    [RelayCommand]
    public void Show(string? which)
    {
        Kind = which == CalendarRules.Week ? CalendarRules.Week : CalendarRules.Month;
        Refresh();
    }

    /// <summary>The week or month before the one on show.</summary>
    [RelayCommand]
    public void Back() => Step(-1);

    /// <summary>The week or month after the one on show.</summary>
    [RelayCommand]
    public void Forward() => Step(1);

    /// <summary>Back to the week or month holding today.</summary>
    [RelayCommand]
    public void Today_()
    {
        anchor = null;
        selected = null;
        Refresh();
    }

    /// <summary>Moves a task to another day, which is what dragging it onto a cell does.</summary>
    public void MoveTo(string taskId, DateOnly day)
    {
        tasks.Plan(taskId, day);
        selected = day;
        Refresh();
    }

    /// <summary>Opens an event's editor over the page, from its bar, the open day or Today's line.</summary>
    public void OpenEvent(string id)
    {
        if (events?.Get(id) is { } item)
        {
            Editor!.Open(item);
        }
    }

    /// <summary>Opens a day, or closes it when it is already open. While several days are picked, it goes back to this one.</summary>
    public void Open(DateOnly day)
    {
        selected = picks.Count > 1 || selected != day ? day : null;
        picks = [];
        Refresh();
    }

    private static IEnumerable<(TaskItem Task, string Label)> Entries(CalendarDay day) =>
    [
        .. day.Planned.Select(task => (task, "Calendar.Planned")),
        .. day.Deadlines.Select(task => (task, "Calendar.Deadline")),
        .. day.Repeats.Select(task => (task, "Calendar.Repeat")),
    ];

    [RelayCommand]
    private void Undo()
    {
        var action = undo;
        HideUndo();
        action?.Invoke();
    }

    // The week rows over the cells already built: each row's bars on the first three lanes, and "+N"
    // under a day whose events need more.
    private void ShowWeeks(IReadOnlyList<EventItem> shownEvents, DateOnly start, DateOnly end)
    {
        var rows = EventRules.Bars(shownEvents, start, end);
        Weeks.Clear();
        for (var row = 0; row < rows.Count; row++)
        {
            var bars = rows[row];
            var drawn = bars
                .Where(bar => bar.Lane < CalendarWeekViewModel.Lanes)
                .Select(bar => new CalendarBarViewModel(
                    bar.Event.Id,
                    bar.Event.Title,
                    bar.From,
                    bar.To - bar.From + 1,
                    bar.Lane,
                    bar.Before,
                    bar.After,
                    ColourOf(bar.Event),
                    EventText.Name(bar.Event, strings),
                    () => OpenEvent(bar.Event.Id)))
                .ToList();
            var more = Enumerable.Range(0, 7)
                .Select(column => (Column: column, Count: bars.Count(bar => bar.Lane >= CalendarWeekViewModel.Lanes && bar.From <= column && bar.To >= column)))
                .Where(day => day.Count > 0)
                .Select(day => new CalendarMoreViewModel(day.Column, strings.Get("Calendar.EventsMore", day.Count), strings.Get("Calendar.EventsMoreName", day.Count)))
                .ToList();
            Weeks.Add(new CalendarWeekViewModel([.. Cells.Skip(row * 7).Take(7)], drawn, more));
        }
    }

    private Brush? ColourOf(EventItem item) =>
        item.AreaId is { } id && areas.All().FirstOrDefault(area => area.Id == id) is { } area ? areaBrush(area.ColorId) : null;

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

    private void Step(int by)
    {
        var shown = anchor ?? Today();
        anchor = Kind == CalendarRules.Week ? shown.AddDays(7 * by) : shown.AddMonths(by);
        selected = null;
        Refresh();
    }

    private DateOnly Today() => PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);
}
