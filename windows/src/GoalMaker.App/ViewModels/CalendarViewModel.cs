using System.Collections.ObjectModel;
using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The Calendar page (docs/calendar.md, spec story 68): a week or a month of planned tasks, deadlines
/// and reminders, with a day showing what it holds and a task opening from there.
/// </summary>
public sealed partial class CalendarViewModel : ObservableObject
{
    private readonly TaskList tasks;
    private readonly ReminderList reminders;
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly TimeProvider time;
    private readonly Action<string> openTask;
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

    public CalendarViewModel(
        TaskList tasks,
        ReminderList reminders,
        ISettingsStore settings,
        IStrings strings,
        TimeProvider time,
        Action<string> openTask,
        Action<Action> runOnUi)
    {
        this.tasks = tasks;
        this.reminders = reminders;
        this.settings = settings;
        this.strings = strings;
        this.time = time;
        this.openTask = openTask;
        tasks.Changed += (_, _) => runOnUi(Refresh);
        reminders.Changed += (_, _) => runOnUi(Refresh);
        Weekdays = [.. Enumerable.Range(0, 7).Select(day => CultureInfo.CurrentCulture.DateTimeFormat.AbbreviatedDayNames[(day + 1) % 7])];
        Refresh();
    }

    /// <summary>Which of the two views is on show, so its button reads as chosen.</summary>
    public bool IsWeek => Kind == CalendarRules.Week;

    public bool IsMonth => Kind == CalendarRules.Month;

    /// <summary>Whether no day is open, so the page asks the owner to pick one.</summary>
    public bool HasNoDay => !HasDay;

    /// <summary>The weekday headings, Monday first.</summary>
    public IReadOnlyList<string> Weekdays { get; }

    /// <summary>The days of the grid, seven to a row.</summary>
    public ObservableCollection<CalendarCellViewModel> Cells { get; } = [];

    /// <summary>What the day the owner picked holds.</summary>
    public ObservableCollection<CalendarEntryViewModel> DayEntries { get; } = [];

    public void Refresh()
    {
        var today = Today();
        var shown = anchor ?? today;
        var days = CalendarRules.Build(
            tasks.All(),
            reminders.All(),
            CalendarRules.Start(Kind, shown),
            CalendarRules.End(Kind, shown));

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
                () => Open(date)));
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
            foreach (var (task, label) in Entries(open))
            {
                var id = task.Id;
                DayEntries.Add(new CalendarEntryViewModel(
                    id,
                    task.Title,
                    strings.Get(label),
                    task.PlannedTime?.ToString("t", CultureInfo.CurrentCulture) ?? string.Empty,
                    task.State == TaskState.Done,
                    () => openTask(id)));
            }
        }

        DayTitle = open is null ? string.Empty : open.Day.ToString("D", CultureInfo.CurrentCulture);
        HasDay = open is not null;
        IsDayEmpty = open is not null && open.Empty;
        HasReminders = open is { Reminders: > 0 };
        DayReminders = HasReminders ? strings.Get("Calendar.Reminders", open!.Reminders) : string.Empty;
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
