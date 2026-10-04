using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The Habits page (docs/habits.md, spec stories 36 to 42; the habits prototype, option B): a summary
/// card, then every habit in its group (Every day, Weekly, Limits) with today's check-in, its streak,
/// its week and its heatmap, Hide done, and the archived ones folded. Habits are added and edited in
/// <see cref="Editor"/> and amounts logged in the log panel. A streak reaching a milestone raises
/// <see cref="Celebrate"/>, unless motion is reduced.
/// </summary>
public sealed partial class HabitsViewModel : ObservableObject
{
    /// <summary>Streak lengths that get confetti.</summary>
    public static readonly int[] Milestones = [7, 14, 30, 50, 100, 200, 365, 500, 1000];

    // Weeks of heatmap kept per habit; the page shows the last ones that fit, and never fewer than
    // MinWeeks, so a young habit still has a map to fill.
    private const int HeatWeeks = 26;
    private const int MinWeeks = 8;

    // Days in the week's dots on a card: today and the six before it.
    private const int WeekDays = 7;

    private readonly HabitList habits;
    private readonly GoalList goals;
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly TimeProvider time;
    private readonly Func<bool> motionReduced;
    private readonly Action? openMini;
    private HashSet<string>? milestones;
    private string? loggingId;

    // The day a typed amount lands on: today, or the calendar's open day.
    private DateOnly? loggingDay;

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

    /// <summary>Hides the habits done today from their groups; the page's own, kept while the app runs.</summary>
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HideDoneText))]
    private bool hideDone;

    [ObservableProperty]
    private string summaryDate = string.Empty;

    [ObservableProperty]
    private string summaryDone = string.Empty;

    [ObservableProperty]
    private string summaryOf = string.Empty;

    [ObservableProperty]
    private double summaryShare;

    [ObservableProperty]
    private string summaryPercent = string.Empty;

    [ObservableProperty]
    private string summaryBest = string.Empty;

    [ObservableProperty]
    private string summaryName = string.Empty;

    public HabitsViewModel(
        HabitList habits,
        GoalList goals,
        ISettingsStore settings,
        IStrings strings,
        TimeProvider time,
        Func<bool> motionReduced,
        Action<Action> runOnUi,
        Action? openMini = null,
        ChatViewModel? chat = null)
    {
        this.openMini = openMini;
        this.habits = habits;
        this.goals = goals;
        this.settings = settings;
        this.strings = strings;
        this.time = time;
        this.motionReduced = motionReduced;
        Editor = new HabitEditorViewModel(habits, goals, strings, Today);
        Bar = new HabitBarViewModel(habits, strings, Today, Editor.OpenFrom, chat);
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

    /// <summary>The bottom bar: type a habit to add it, or open the editor with its plus.</summary>
    public HabitBarViewModel Bar { get; }

    /// <summary>The page offers the mini window; the mini window itself has nothing to offer.</summary>
    public bool HasMini => openMini is not null;

    /// <summary>Habits on their own, small and out of the way (docs/mini-windows.md).</summary>
    [RelayCommand]
    private void OpenMini() => openMini?.Invoke();

    /// <summary>A habit's streak just reached a milestone: time for confetti.</summary>
    public event EventHandler? Celebrate;

    /// <summary>An amount habit was tapped and the log panel opened: the shell shows the Habits page.</summary>
    public event EventHandler? LogRequested;

    /// <summary>A card on Today asked for the Habits page, or for its editor there: the shell shows the page.</summary>
    public event EventHandler? PageWanted;

    public HabitEditorViewModel Editor { get; }

    public ObservableCollection<HabitRowViewModel> Rows { get; } = [];

    public ObservableCollection<HabitRowViewModel> Archived { get; } = [];

    /// <summary>The page's groups in order, Every day, Weekly and Limits, without the done ones while Hide done is on.</summary>
    public ObservableCollection<HabitGroupViewModel> Groups { get; } = [];

    public string HideDoneText => strings.Get(HideDone ? "Habits.ShowDone" : "Habits.HideDone");

    /// <summary>The habits show unless the editor or the log panel took the page.</summary>
    public bool ShowList => !Editor.IsOpen && !IsLogging;

    public bool HasLogUnit => LogUnit.Length > 0;

    /// <summary>Today's habits for the panel on Today: due today and not kept off Today (contracts/vectors/habits.json, onToday).</summary>
    public IReadOnlyList<HabitRowViewModel> TodayRows() => RowsWhere(HabitRules.OnToday);

    /// <summary>Every habit due today, the ones kept off Today too: what the Places page counts.</summary>
    public IReadOnlyList<HabitRowViewModel> DueRows() => RowsWhere(HabitRules.DueToday);

    /// <summary>
    /// Every habit due on <paramref name="day"/>, as cards that check in, skip and fail on that day: the
    /// calendar's open day (docs/calendar.md).
    /// </summary>
    public IReadOnlyList<HabitRowViewModel> DayRows(DateOnly day)
    {
        var checkins = habits.Checkins();
        var pauses = habits.Pauses();
        return [.. habits.All()
            .Where(habit => HabitRules.DueToday(habit, day, [.. pauses.Where(pause => pause.HabitId == habit.Id)]))
            // Today's own cards say "today"; another day's leave it out.
            .Select(habit => Row(habit, checkins, pauses, day, null, strings, full: false, owner: this, day: day == Today() ? null : day))];
    }

    private List<HabitRowViewModel> RowsWhere(Func<HabitItem, DateOnly, IReadOnlyList<HabitPause>, bool> rule)
    {
        var today = Today();
        var checkins = habits.Checkins();
        var pauses = habits.Pauses();
        return [.. habits.All()
            .Where(habit => rule(habit, today, [.. pauses.Where(pause => pause.HabitId == habit.Id)]))
            .Select(habit => Row(habit, checkins, pauses, today, null, strings, full: false, owner: this))];
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
            var row = Row(habit, checkins, pauses, today, title, strings, full: true, owner: this);
            (habit.Archived ? Archived : Rows).Add(row);
        }

        ShowGroups();
        ShowSummary(today);

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

    partial void OnHideDoneChanged(bool value) => ShowGroups();

    // Every day, Weekly and Limits (contracts/vectors/habits.json, groups), so a limit never reads as not done.
    private void ShowGroups()
    {
        Groups.Clear();
        foreach (var group in Enum.GetValues<HabitGroup>())
        {
            var all = Rows.Where(row => row.Group == group).ToList();
            if (all.Count == 0)
            {
                continue;
            }

            var name = strings.Get(group switch
            {
                HabitGroup.Days => "Habits.GroupDays",
                HabitGroup.Weekly => "Habits.GroupWeekly",
                _ => "Habits.GroupLimits",
            });
            var header = strings.Get("Habits.GroupHeader", name, all.Count).ToUpper(System.Globalization.CultureInfo.CurrentUICulture);
            Groups.Add(new HabitGroupViewModel(group, header, [.. all.Where(row => !(HideDone && row.IsDone))], all.Count));
        }
    }

    // The summary card: of the habits that ask something of today (limits, skips and pauses ask nothing,
    // a failed one asked and didn't get it),
    // how many are done, how far the day has got (one still to do counts its ring), and the longest streak.
    private void ShowSummary(DateOnly today)
    {
        var asking = Rows.Where(row => row.Standing is HabitStanding.Done or HabitStanding.Left or HabitStanding.Failed).ToList();
        var done = asking.Count(row => row.IsDone);
        SummaryShare = asking.Count == 0 ? 0 : asking.Sum(row => row.IsDone ? 1 : row.Fraction) / asking.Count;
        SummaryDate = today.ToString("dddd d MMMM", System.Globalization.CultureInfo.CurrentCulture).ToUpper(System.Globalization.CultureInfo.CurrentUICulture);
        SummaryDone = done.ToString(System.Globalization.CultureInfo.CurrentCulture);
        SummaryOf = strings.Get("Habits.SummaryOf", asking.Count);
        SummaryPercent = SummaryShare.ToString("P0", System.Globalization.CultureInfo.CurrentCulture);
        var best = Rows.Where(row => row.Streak > 0).MaxBy(row => row.Streak);
        SummaryBest = best is null
            ? strings.Get("Habits.SummaryNoStreak")
            : strings.Get("Habits.SummaryBest", best.Habit.Emoji is { } emoji ? $"{emoji} {best.Name}" : best.Name, best.StreakText);
        SummaryName = strings.Get("Habits.SummaryName", done, asking.Count) + ". " + SummaryBest;
    }

    /// <summary>The Habits page itself, asked for from a card on Today.</summary>
    internal void OpenPage() => PageWanted?.Invoke(this, EventArgs.Empty);

    /// <summary>A tap on a habit's ring: a check toggles, a count adds one, an amount asks for its value.</summary>
    internal void Tap(HabitItem habit, DateOnly? day = null)
    {
        if (!habits.Tap(habit.Id, day ?? Today()))
        {
            StartLog(habit, day);
        }
    }

    internal void Edit(HabitItem habit)
    {
        Editor.OpenEdit(habit);
        PageWanted?.Invoke(this, EventArgs.Empty);
    }

    internal void ClearToday(string id, DateOnly? day = null) => habits.SetValue(id, day ?? Today(), 0);

    internal void Skip(string id, bool skipped, DateOnly? day = null) => habits.Skip(id, day ?? Today(), skipped);

    /// <summary>Fails today's period (it won't happen: missed now, the streak ends) or takes the fail back.</summary>
    internal void Fail(string id, bool failed, DateOnly? day = null) => habits.Fail(id, day ?? Today(), failed);

    internal void Pause(string id) => habits.Pause(id, Today());

    internal void Resume(string id) => habits.Resume(id, Today());

    internal void SetArchived(string id, bool archived) => habits.SetArchived(id, archived);

    internal void Delete(string id) => habits.Delete(id);

    /// <summary>
    /// One more of whatever the habit counts, straight from its row, which is the whole act for
    /// a glass of water or a cigarette (docs/habits.md). The panel stays for anything else.
    /// </summary>
    internal void LogOne(HabitItem habit, DateOnly? day = null) => habits.CheckIn(habit.Id, day ?? Today());

    /// <summary>The number typed beside the row. Anything that is not one is left alone.</summary>
    internal bool LogTyped(HabitItem habit, string text, DateOnly? day = null)
    {
        if (GoalEditorViewModel.ParseAmount(text) is not { } amount || amount <= 0)
        {
            return false;
        }

        habits.CheckIn(habit.Id, day ?? Today(), amount);
        return true;
    }

    internal void StartLog(HabitItem habit, DateOnly? day = null)
    {
        loggingId = habit.Id;
        loggingDay = day;
        LogTitle = day is { } on && on != Today()
            ? strings.Get("Habits.LogTitleOn", habit.Name, on.ToString("ddd d MMM", System.Globalization.CultureInfo.CurrentCulture))
            : strings.Get("Habits.LogTitle", habit.Name);
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
        bool full,
        HabitsViewModel? owner,
        DateOnly? day = null)
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
            full
                ? [.. Days(heatStart, today).Select(day => HabitRules.Heat(habit, day, checkins, pauses))]
                : [],
            strings,
            owner,
            HabitRules.Standing(habit, today, checkins, pauses),
            [.. Days(today.AddDays(1 - WeekDays), today).Select(day => new HabitDotViewModel(
                HabitRules.Dot(habit, day, today, checkins, pauses),
                System.Globalization.CultureInfo.CurrentCulture.DateTimeFormat.ShortestDayNames[(int)day.DayOfWeek][..1]
                    .ToUpper(System.Globalization.CultureInfo.CurrentCulture),
                day == today))],
            full,
            day);
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
            habits.CheckIn(id, loggingDay ?? Today(), amount);
        }

        IsLogging = false;
    }

    [RelayCommand]
    private void CancelLog() => IsLogging = false;

    [RelayCommand]
    private void ToggleArchived() => IsArchivedExpanded = !IsArchivedExpanded;

    private DateOnly Today() => PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);
}
