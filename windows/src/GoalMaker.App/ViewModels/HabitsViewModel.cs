using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The Habits page (docs/habits.md, spec stories 36 to 42): every habit with today's ring, its streak
/// and its heatmap. Habits are added and edited in <see cref="Editor"/> and amounts logged in the log
/// panel. A streak reaching a milestone raises <see cref="Celebrate"/>, unless motion is reduced.
/// </summary>
public sealed partial class HabitsViewModel : ObservableObject
{
    /// <summary>Streak lengths that get confetti.</summary>
    public static readonly int[] Milestones = [7, 14, 30, 50, 100, 200, 365, 500, 1000];

    // Weeks of heatmap kept per habit; the page shows the last ones that fit, and never fewer than
    // MinWeeks, so a young habit still has a map to fill.
    private const int HeatWeeks = 26;
    private const int MinWeeks = 8;

    private readonly HabitList habits;
    private readonly GoalList goals;
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly TimeProvider time;
    private readonly Func<bool> motionReduced;
    private readonly Action? openMini;
    private HashSet<string>? milestones;
    private string? loggingId;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(ShowList))]
    private bool isLogging;

    [ObservableProperty]
    private string logTitle = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasLogUnit))]
    private string logUnit = string.Empty;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(AddAmountCommand))]
    private string logText = string.Empty;

    [ObservableProperty]
    private bool isEmpty;

    [ObservableProperty]
    private string archivedHeader = string.Empty;

    [ObservableProperty]
    private bool hasArchived;

    [ObservableProperty]
    private bool isArchivedExpanded;

    public HabitsViewModel(
        HabitList habits, GoalList goals, ISettingsStore settings, IStrings strings, TimeProvider time, Func<bool> motionReduced, Action<Action> runOnUi, Action? openMini = null)
    {
        this.openMini = openMini;
        this.habits = habits;
        this.goals = goals;
        this.settings = settings;
        this.strings = strings;
        this.time = time;
        this.motionReduced = motionReduced;
        Editor = new HabitEditorViewModel(habits, goals, strings, Today);
        Editor.PropertyChanged += (_, change) =>
        {
            if (change.PropertyName == nameof(HabitEditorViewModel.IsOpen))
            {
                OnPropertyChanged(nameof(ShowList));
            }
        };
        habits.Changed += (_, _) => runOnUi(Refresh);
        goals.Changed += (_, _) => runOnUi(Refresh);
        Refresh();
    }

    /// <summary>The page offers the mini window; the mini window itself has nothing to offer.</summary>
    public bool HasMini => openMini is not null;

    /// <summary>Habits on their own, small and out of the way (docs/mini-windows.md).</summary>
    [RelayCommand]
    private void OpenMini() => openMini?.Invoke();

    /// <summary>A habit's streak just reached a milestone: time for confetti.</summary>
    public event EventHandler? Celebrate;

    /// <summary>An amount habit was tapped and the log panel opened: the shell shows the Habits page.</summary>
    public event EventHandler? LogRequested;

    public HabitEditorViewModel Editor { get; }

    public ObservableCollection<HabitRowViewModel> Rows { get; } = [];

    public ObservableCollection<HabitRowViewModel> Archived { get; } = [];

    /// <summary>The habits show unless the editor or the log panel took the page.</summary>
    public bool ShowList => !Editor.IsOpen && !IsLogging;

    public bool HasLogUnit => LogUnit.Length > 0;

    /// <summary>Today's habits for the ring row on Today: active, not paused, and due today.</summary>
    public IReadOnlyList<HabitRowViewModel> TodayRows()
    {
        var today = Today();
        var checkins = habits.Checkins();
        var pauses = habits.Pauses();
        return [.. habits.All()
            .Where(habit => !habit.Archived && habit.StartsOn <= today && HabitRules.IsDue(habit, today))
            .Select(habit => Row(habit, checkins, pauses, today, null, strings, heat: false, owner: this))
            .Where(row => !row.IsPaused)];
    }

    public void Refresh()
    {
        var today = Today();
        var checkins = habits.Checkins();
        var pauses = habits.Pauses();
        var goalTitles = goals.All().ToDictionary(goal => goal.Id, goal => goal.Title, StringComparer.Ordinal);
        Rows.Clear();
        Archived.Clear();
        foreach (var habit in habits.All())
        {
            var title = habit.GoalId is { } goalId && goalTitles.TryGetValue(goalId, out var found) ? found : null;
            var row = Row(habit, checkins, pauses, today, title, strings, heat: true, owner: this);
            (habit.Archived ? Archived : Rows).Add(row);
        }

        IsEmpty = Rows.Count == 0;
        HasArchived = Archived.Count > 0;
        ArchivedHeader = strings.Get("Habits.Archived", Archived.Count).ToUpper(System.Globalization.CultureInfo.CurrentUICulture);

        // Confetti for a streak that reached a milestone since the last look (design spec, level 3).
        var reached = Rows
            .Where(row => Milestones.Contains(row.Streak) && row.Fraction >= 1)
            .Select(row => $"{row.Id}:{row.Streak}")
            .ToHashSet(StringComparer.Ordinal);
        var before = milestones;
        milestones = reached;
        if (before is not null && !reached.IsSubsetOf(before) && !motionReduced())
        {
            Celebrate?.Invoke(this, EventArgs.Empty);
        }
    }

    /// <summary>A tap on a habit's ring: a check toggles, a count adds one, an amount asks for its value.</summary>
    internal void Tap(HabitItem habit)
    {
        if (!habits.Tap(habit.Id, Today()))
        {
            StartLog(habit);
        }
    }

    internal void Edit(HabitItem habit) => Editor.OpenEdit(habit);

    internal void ClearToday(string id) => habits.SetValue(id, Today(), 0);

    internal void Skip(string id, bool skipped) => habits.Skip(id, Today(), skipped);

    internal void Pause(string id) => habits.Pause(id, Today());

    internal void Resume(string id) => habits.Resume(id, Today());

    internal void SetArchived(string id, bool archived) => habits.SetArchived(id, archived);

    internal void Delete(string id) => habits.Delete(id);

    internal void StartLog(HabitItem habit)
    {
        loggingId = habit.Id;
        LogTitle = strings.Get("Habits.LogTitle", habit.Name);
        LogUnit = habit.Unit ?? string.Empty;
        LogText = string.Empty;
        IsLogging = true;
        LogRequested?.Invoke(this, EventArgs.Empty);
    }

    private static HabitRowViewModel Row(
        HabitItem habit,
        IReadOnlyList<HabitCheckin> allCheckins,
        IReadOnlyList<HabitPause> allPauses,
        DateOnly today,
        string? goalTitle,
        IStrings strings,
        bool heat,
        HabitsViewModel? owner)
    {
        var checkins = allCheckins.Where(checkin => checkin.HabitId == habit.Id).ToList();
        var pauses = allPauses.Where(pause => pause.HabitId == habit.Id).ToList();
        var start = HabitRules.PeriodStart(habit, today);
        var end = HabitRules.PeriodEnd(habit, start);
        var inPeriod = checkins.Where(checkin => checkin.Day >= start && checkin.Day <= end).ToList();
        // The map starts at the Monday of the habit's first week, at most HeatWeeks back and at least
        // MinWeeks wide.
        var earliest = Monday(today.AddDays(-7 * (HeatWeeks - 1)));
        var heatStart = Monday(habit.StartsOn) > earliest ? Monday(habit.StartsOn) : earliest;
        var widest = Monday(today.AddDays(-7 * (MinWeeks - 1)));
        heatStart = heatStart > widest ? widest : heatStart;
        return new HabitRowViewModel(
            habit,
            HabitRules.Ring(habit, today, checkins),
            HabitRules.Streak(habit, today, checkins, pauses),
            checkins.FirstOrDefault(checkin => checkin.Day == today && !checkin.Skipped)?.Value ?? 0,
            inPeriod.Count(checkin => HabitRules.DayMet(habit, checkin)),
            inPeriod.Any(checkin => checkin.Skipped),
            pauses.Any(pause => pause.From <= today && (pause.Until is null || pause.Until >= today)),
            goalTitle,
            heat
                ? [.. Days(heatStart, today).Select(day => HabitRules.Heat(habit, day, checkins, pauses))]
                : [],
            strings,
            owner);
    }

    private static DateOnly Monday(DateOnly day) => day.AddDays(-(((int)day.DayOfWeek + 6) % 7));

    private static IEnumerable<DateOnly> Days(DateOnly from, DateOnly to)
    {
        for (var day = from; day <= to; day = day.AddDays(1))
        {
            yield return day;
        }
    }

    private bool CanLogAmount() => GoalEditorViewModel.ParseAmount(LogText) is > 0;

    [RelayCommand]
    private void NewHabit() => Editor.OpenNew();

    [RelayCommand(CanExecute = nameof(CanLogAmount))]
    private void AddAmount()
    {
        if (loggingId is { } id && GoalEditorViewModel.ParseAmount(LogText) is { } amount)
        {
            habits.CheckIn(id, Today(), amount);
        }

        IsLogging = false;
    }

    [RelayCommand]
    private void CancelLog() => IsLogging = false;

    [RelayCommand]
    private void ToggleArchived() => IsArchivedExpanded = !IsArchivedExpanded;

    private DateOnly Today() => PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);
}
