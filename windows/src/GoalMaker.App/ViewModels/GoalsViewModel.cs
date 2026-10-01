using System.Collections.ObjectModel;
using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The Goals page (docs/goals.md, spec stories 27 to 35): the horizon rings on top, then the ladder as
/// four columns from this year down to today with each goal as a compact card, then next week for
/// planning ahead. A ring shows only its horizon (the other columns fade); clicking a card lights what
/// it feeds and what feeds it. Goals are added and edited in <see cref="Editor"/> and amounts are logged
/// in the log panel. A shown goal that becomes a hit raises <see cref="Celebrate"/>, unless motion is reduced.
/// The page header switches to the plain list (<see cref="View"/>): the same periods as groups of compact
/// rows, where a click opens the goal; this PC remembers the choice.
/// </summary>
public sealed partial class GoalsViewModel : ObservableObject
{
    private readonly GoalList goals;
    private readonly TaskList tasks;
    private readonly HabitList? habits;
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly TimeProvider time;
    private readonly Func<bool> motionReduced;
    private HashSet<string>? hits;
    private string? loggingId;
    private IReadOnlyList<GoalItem> open = [];

    [ObservableProperty]
    private GoalHorizon? filter;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsLadderView), nameof(IsListView))]
    private GoalsView view;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasPick), nameof(HintText))]
    private GoalRowViewModel? picked;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HintText))]
    private int behind;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(ShowList))]
    private bool isLogging;

    [ObservableProperty]
    private string logTitle = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasLogUnit))]
    private string logUnit = string.Empty;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(AddAmountCommand), nameof(TakeOffAmountCommand))]
    private string logText = string.Empty;

    public GoalsViewModel(
        GoalList goals,
        TaskList tasks,
        ISettingsStore settings,
        IStrings strings,
        TimeProvider time,
        Func<bool> motionReduced,
        Action<Action> runOnUi,
        HabitList? habits = null,
        ChatViewModel? chat = null)
    {
        this.goals = goals;
        this.tasks = tasks;
        this.habits = habits;
        this.settings = settings;
        this.strings = strings;
        this.time = time;
        this.motionReduced = motionReduced;
        View = settings.GoalsView;
        Editor = new GoalEditorViewModel(goals, strings, Today);
        Bar = new GoalBarViewModel(goals, strings, Today, Editor.OpenFrom, chat);
        Editor.PropertyChanged += (_, change) =>
        {
            if (change.PropertyName == nameof(GoalEditorViewModel.IsOpen))
            {
                OnPropertyChanged(nameof(ShowList));
            }
        };
        goals.Changed += (_, _) => runOnUi(Refresh);
        tasks.Changed += (_, _) => runOnUi(Refresh);
        if (habits is not null)
        {
            habits.Changed += (_, _) => runOnUi(Refresh);
        }

        Refresh();
    }

    /// <summary>A shown goal just became a hit: time for confetti.</summary>
    public event EventHandler? Celebrate;

    public GoalEditorViewModel Editor { get; }

    /// <summary>The bottom bar: type a goal to add it, or open the editor with its plus.</summary>
    public GoalBarViewModel Bar { get; }

    /// <summary>This year, month, week and today, then next week.</summary>
    public ObservableCollection<GoalSectionViewModel> Sections { get; } = [];

    /// <summary>The ladder's columns: this year, month, week and today.</summary>
    public ObservableCollection<GoalSectionViewModel> Lanes { get; } = [];

    /// <summary>The dashboard: one ring per column.</summary>
    public ObservableCollection<HorizonRingViewModel> Rings { get; } = [];

    /// <summary>Next week, for planning ahead.</summary>
    public GoalSectionViewModel? NextWeek => Sections.Count > 4 ? Sections[4] : null;

    public bool HasPick => Picked is not null;

    /// <summary>The rings, the ladder's columns and next week show; the header's Ladder choice.</summary>
    public bool IsLadderView
    {
        get => View == GoalsView.Ladder;
        set
        {
            if (value)
            {
                View = GoalsView.Ladder;
            }
        }
    }

    /// <summary>The plain list shows: <see cref="Sections"/> as groups of compact rows; the header's List choice.</summary>
    public bool IsListView
    {
        get => View == GoalsView.List;
        set
        {
            if (value)
            {
                View = GoalsView.List;
            }
        }
    }

    /// <summary>The line under the rings: how to light a chain and how many goals need you, or the lit chain.</summary>
    public string HintText => Picked is { } row
        ? strings.Get("Goals.ChainOf", row.Title)
        : Behind > 0
            ? $"{strings.Get("Goals.Hint")} {strings.Get(Behind == 1 ? "Goals.NeedYouOne" : "Goals.NeedYouMany", Behind)}"
            : strings.Get("Goals.Hint");

    /// <summary>The goals show unless the editor or the log panel took the page.</summary>
    public bool ShowList => !Editor.IsOpen && !IsLogging;

    public bool HasLogUnit => LogUnit.Length > 0;

    /// <summary>A period by its dates: "2026", "September 2026", "14 to 20 Sep", "Saturday 19 September".</summary>
    public static string PeriodText(GoalHorizon horizon, DateOnly start, IStrings strings) => horizon switch
    {
        GoalHorizon.Year => start.Year.ToString(CultureInfo.CurrentCulture),
        GoalHorizon.Month => start.ToString("MMMM yyyy", CultureInfo.CurrentCulture),
        GoalHorizon.Week => strings.Get(
            "Goals.Range",
            start.ToString(start.Month == start.AddDays(6).Month ? "%d" : "d MMM", CultureInfo.CurrentCulture),
            start.AddDays(6).ToString("d MMM", CultureInfo.CurrentCulture)),
        _ => start.ToString("dddd d MMMM", CultureInfo.CurrentCulture),
    };

    /// <summary>This week's goals with where they stand, the ones that need you first, for Today's folded section.</summary>
    public static IReadOnlyList<GoalRowViewModel> ThisWeek(GoalList goals, TaskList tasks, DateOnly today, IStrings strings, HabitList? habits = null)
    {
        var week = GoalRules.PeriodStart(GoalHorizon.Week, today);
        var all = goals.All();
        var progress = ProgressOf(goals.Entries(), tasks.All(), habits);
        var rows = all.Where(goal => goal.Horizon == GoalHorizon.Week && goal.PeriodStart == week && goal.Status != GoalRules.Dropped)
            .Select(goal =>
            {
                var where = progress(goal);
                return new GoalRowViewModel(goal, where, null, strings, standing: GoalRules.Standing(goal, where, today));
            });
        return GoalRules.ByPace(rows, row => row.Pace);
    }

    public void Refresh()
    {
        var today = Today();
        var all = goals.All();
        var byId = all.ToDictionary(goal => goal.Id, StringComparer.Ordinal);
        var entries = goals.Entries().ToLookup(entry => entry.GoalId, StringComparer.Ordinal);
        var progress = ProgressOf(entries.SelectMany(group => group), tasks.All(), habits);
        GoalRowViewModel Row(GoalItem goal)
        {
            var where = progress(goal);
            return new(
                goal,
                where,
                goal.ParentId is { } id && byId.TryGetValue(id, out var parent) ? parent.Title : null,
                strings,
                this,
                GoalRules.Standing(goal, where, today),
                GoalRules.QuickAmount(entries[goal.Id]));
        }

        bool Kept(GoalItem goal, GoalHorizon horizon, DateOnly start) =>
            goal.Horizon == horizon && goal.PeriodStart == start && goal.Status != GoalRules.Dropped;

        var week = GoalRules.PeriodStart(GoalHorizon.Week, today);
        var periods = new (GoalHorizon Horizon, DateOnly Start, string Name)[]
        {
            (GoalHorizon.Year, GoalRules.PeriodStart(GoalHorizon.Year, today), strings.Get("Goals.ThisYear")),
            (GoalHorizon.Month, GoalRules.PeriodStart(GoalHorizon.Month, today), strings.Get("Goals.ThisMonth")),
            (GoalHorizon.Week, week, strings.Get("Goals.ThisWeek")),
            (GoalHorizon.Day, today, strings.Get("Goals.ThisDay")),
            (GoalHorizon.Week, week.AddDays(7), strings.Get("Goals.NextWeek")),
        };
        Sections.Clear();
        Lanes.Clear();
        Rings.Clear();
        for (var index = 0; index < periods.Length; index++)
        {
            var (horizon, start, name) = periods[index];
            var next = index == periods.Length - 1;
            var own = all.Where(goal => Kept(goal, horizon, start)).ToList();
            // A new week, month or year with no goals yet can start from the last one's (story 35);
            // next week's copies this week's.
            var previous = GoalRules.PeriodStart(horizon, start.AddDays(-1));
            var canCopy = own.Count == 0 && horizon != GoalHorizon.Day && all.Any(goal => Kept(goal, horizon, previous));
            var rows = GoalRules.ByPace(own.Select(Row), row => row.Pace);
            var section = new GoalSectionViewModel(
                horizon,
                start,
                Upper(strings.Get("Goals.Section", name, PeriodText(horizon, start, strings))),
                rows,
                canCopy,
                strings.Get(next ? "Goals.CopyThisWeek" : "Goals.Copy" + horizon),
                section => Editor.OpenNew(section.Horizon, section.Start),
                section => goals.CopyPrevious(section.Horizon, section.Start))
            {
                AddName = strings.Get("Goals.AddTo", strings.Get("Goals.Section", name, PeriodText(horizon, start, strings))),
                HitText = rows.Count == 0 ? string.Empty : strings.Get("Goals.RingHit", rows.Count(row => row.IsHit), rows.Count),
                Badge = strings.Get("Goals.Badge" + horizon),
                Title = horizon == GoalHorizon.Month ? start.ToString("MMMM", CultureInfo.CurrentCulture) : PeriodText(horizon, start, strings),
                Line = strings.Get("Goals.LaneLine", strings.Get("Goals.RingHit", rows.Count(row => row.IsHit), rows.Count), Gone(horizon, start, today)),
            };
            Sections.Add(section);
            if (!next)
            {
                Lanes.Add(section);
                Rings.Add(new HorizonRingViewModel(section, strings, ToggleFilter));
            }
        }

        OnPropertyChanged(nameof(NextWeek));
        open = [.. all.Where(goal => goal.Status != GoalRules.Dropped)];
        Behind = Lanes.SelectMany(lane => lane.Rows).Count(row => row.Pace == GoalPace.Behind);
        var pickedId = Picked?.Id;
        Picked = pickedId is null ? null : Sections.SelectMany(section => section.Rows).FirstOrDefault(row => row.Id == pickedId);
        ApplyFocus();

        // Confetti for a goal that became a hit since the last look (design spec, level 3).
        var nowHits = Sections.SelectMany(section => section.Rows).Where(row => row.IsHit).Select(row => row.Id).ToHashSet(StringComparer.Ordinal);
        var before = hits;
        hits = nowHits;
        if (before is not null && !nowHits.IsSubsetOf(before) && !motionReduced())
        {
            Celebrate?.Invoke(this, EventArgs.Empty);
        }
    }

    /// <summary>Shows only <paramref name="horizon"/>'s column, or every column when it was already the one shown.</summary>
    public void ToggleFilter(GoalHorizon horizon)
    {
        Filter = Filter == horizon ? null : horizon;
        ApplyFocus();
    }

    /// <summary>Lights the chain of the goal <paramref name="id"/>, or puts it out when it was already picked.</summary>
    public void Pick(string id)
    {
        Picked = Picked?.Id == id ? null : Sections.SelectMany(section => section.Rows).FirstOrDefault(row => row.Id == id);
        ApplyFocus();
    }

    internal void Edit(GoalItem goal) => Editor.OpenEdit(goal);

    // The list has no chain to light, so a lit one goes out; the choice is this PC's.
    partial void OnViewChanged(GoalsView value)
    {
        if (value == GoalsView.List && Picked is not null)
        {
            ClearPick();
        }

        if (settings.GoalsView != value)
        {
            settings.GoalsView = value;
        }
    }

    internal void SetStatus(string id, string status) => goals.SetStatus(id, status);

    internal void Delete(string id) => goals.Delete(id);

    /// <summary>
    /// The card's quick log: a numeric goal gets its latest amount again; with nothing logged yet the log
    /// panel asks for one.
    /// </summary>
    internal void QuickLog(GoalRowViewModel row)
    {
        if (row.Goal.Mode != GoalRules.ModeNumber)
        {
            return;
        }

        if (row.QuickAmount is { } amount)
        {
            goals.LogAmount(row.Id, Today(), amount);
        }
        else
        {
            StartLog(row.Goal);
        }
    }

    internal void StartLog(GoalItem goal)
    {
        loggingId = goal.Id;
        LogTitle = strings.Get("Goals.LogTitle", goal.Title);
        LogUnit = goal.Unit ?? string.Empty;
        LogText = string.Empty;
        IsLogging = true;
    }

    /// <summary>Puts the lit chain out.</summary>
    [RelayCommand]
    private void ClearPick()
    {
        Picked = null;
        ApplyFocus();
    }

    private bool CanLogAmount() => GoalEditorViewModel.ParseAmount(LogText) is > 0;

    [RelayCommand(CanExecute = nameof(CanLogAmount))]
    private void AddAmount() => Log(1);

    /// <summary>Logs the amount negative, to correct one logged by mistake.</summary>
    [RelayCommand(CanExecute = nameof(CanLogAmount))]
    private void TakeOffAmount() => Log(-1);

    [RelayCommand]
    private void CancelLog() => IsLogging = false;

    private void Log(int sign)
    {
        if (loggingId is { } id && GoalEditorViewModel.ParseAmount(LogText) is { } amount)
        {
            goals.LogAmount(id, Today(), sign * amount);
        }

        IsLogging = false;
    }

    // Fades the columns a ring doesn't show, and the cards outside the lit chain.
    private void ApplyFocus()
    {
        foreach (var ring in Rings)
        {
            ring.IsShown = ring.Horizon == Filter;
        }

        foreach (var section in Sections)
        {
            section.IsDimmed = Filter is { } only && section.Horizon != only;
        }

        var chain = Picked is { } row ? GoalRules.Chain(open, row.Id) : null;
        foreach (var card in Sections.SelectMany(section => section.Rows))
        {
            card.IsPicked = card.Id == Picked?.Id;
            card.IsLit = chain?.Contains(card.Id) == true;
            card.IsDimmed = chain is not null && !card.IsLit;
        }
    }

    private DateOnly Today() => PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);

    // How much of a column's period is gone: "75% of the year gone", "Day 5 of 7", "Today".
    private string Gone(GoalHorizon horizon, DateOnly start, DateOnly today)
    {
        var length = GoalRules.PeriodEnd(horizon, start).DayNumber - start.DayNumber + 1;
        var day = Math.Clamp(today.DayNumber - start.DayNumber + 1, 1, length);
        return horizon switch
        {
            GoalHorizon.Year => strings.Get("Goals.YearGone", GoalRules.Elapsed(horizon, start, today).ToString("P0", CultureInfo.CurrentCulture)),
            GoalHorizon.Day => strings.Get("Goals.ThisDay"),
            _ => strings.Get("Goals.DayOf", day, length),
        };
    }

    // Progress from the tasks that serve a goal, the amounts logged on it, and the check-ins of habits
    // serving it in its unit (story 32, docs/habits.md).
    private static Func<GoalItem, GoalProgress> ProgressOf(IEnumerable<GoalEntryItem> entries, IEnumerable<TaskItem> taskList, HabitList? habits)
    {
        var entriesByGoal = entries.ToLookup(entry => entry.GoalId, StringComparer.Ordinal);
        var tasksByGoal = taskList.Where(task => task.GoalId is not null).ToLookup(task => task.GoalId!, StringComparer.Ordinal);
        var habitList = habits?.All() ?? [];
        var checkins = habits?.Checkins() ?? [];
        return goal => GoalRules.ProgressOf(goal, tasksByGoal[goal.Id], entriesByGoal[goal.Id], habitList, checkins);
    }

    private static string Upper(string text) => text.ToUpper(CultureInfo.CurrentUICulture);
}
