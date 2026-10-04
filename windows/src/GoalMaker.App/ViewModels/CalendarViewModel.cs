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
    private readonly HabitsViewModel? habitsPage;
    private DateOnly? anchor;
    private DateOnly? selected;

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
        HabitList? habitList = null)
    {
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
        Refresh();
    }

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

    /// <summary>What the day the owner picked holds.</summary>
    public ObservableCollection<CalendarEntryViewModel> DayEntries { get; } = [];

    /// <summary>
    /// What a reader says for a day's cell: "Saturday, 3 October 2026, today, 2 planned, 1 due", or that
    /// nothing is on it. The same parts, in the same order, as the phone's cell.
    /// </summary>
    private string CellName(CalendarDay day, bool isToday, bool isOpen)
    {
        var parts = new List<string> { day.Day.ToString("D", CultureInfo.CurrentCulture) };
        if (isToday)
        {
            parts.Add(strings.Get("Calendar.CellToday"));
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
        if (day.Empty)
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
        var days = CalendarRules.Build(
            tasks.All(),
            reminders.All(),
            CalendarRules.Start(Kind, shown),
            CalendarRules.End(Kind, shown),
            links is null ? null : task => narrowed.Keeps(task, links, projectAreas));

        Cells.Clear();
        foreach (var day in days)
        {
            var date = day.Day;
            Cells.Add(new CalendarCellViewModel(
                date,
                date.Day.ToString(CultureInfo.CurrentCulture),
                day.Count,
                date == today,
                Kind == CalendarRules.Week || date.Month == shown.Month,
                date == selected,
                () => Open(date),
                CellName(day, date == today, date == selected)));
        }

        Period = Kind == CalendarRules.Month
            ? shown.ToString("MMMM yyyy", CultureInfo.CurrentCulture)
            : strings.Get(
                "Goals.Range",
                CalendarRules.Start(CalendarRules.Week, shown).ToString("d MMM", CultureInfo.CurrentCulture),
                CalendarRules.End(CalendarRules.Week, shown).ToString("d MMM", CultureInfo.CurrentCulture));

        var open = days.FirstOrDefault(day => day.Day == selected);
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
        DayHabits = open is not null && open.Day <= today && habitsPage is not null ? habitsPage.DayRows(open.Day) : [];

        DayTitle = open is null ? string.Empty : open.Day.ToString("D", CultureInfo.CurrentCulture);
        HasDay = open is not null;
        IsDayEmpty = open is not null && open.Empty;
        HasReminders = open is { Reminders: > 0 };
        DayReminders = HasReminders ? strings.Get("Calendar.Reminders", open!.Reminders) : string.Empty;
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

    /// <summary>Opens a day, or closes it when it is already open.</summary>
    public void Open(DateOnly day)
    {
        selected = selected == day ? null : day;
        Refresh();
    }

    private static IEnumerable<(TaskItem Task, string Label)> Entries(CalendarDay day) =>
    [
        .. day.Planned.Select(task => (task, "Calendar.Planned")),
        .. day.Deadlines.Select(task => (task, "Calendar.Deadline")),
        .. day.Repeats.Select(task => (task, "Calendar.Repeat")),
    ];

    private void Step(int by)
    {
        var shown = anchor ?? Today();
        anchor = Kind == CalendarRules.Week ? shown.AddDays(7 * by) : shown.AddMonths(by);
        selected = null;
        Refresh();
    }

    private DateOnly Today() => PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);
}
