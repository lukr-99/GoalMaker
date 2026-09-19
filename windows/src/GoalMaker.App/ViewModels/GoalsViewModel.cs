using System.Collections.ObjectModel;
using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The Goals page (docs/goals.md, spec stories 27 to 35): this year's, month's, week's and today's
/// goals with their rings, next week's for planning ahead, or all of them as the cascade. Goals are
/// added and edited in <see cref="Editor"/> and amounts are logged in the log panel. A shown goal that
/// becomes a hit raises <see cref="Celebrate"/>, unless motion is reduced.
/// </summary>
public sealed partial class GoalsViewModel : ObservableObject
{
    private readonly GoalList goals;
    private readonly TaskList tasks;
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly TimeProvider time;
    private readonly Func<bool> motionReduced;
    private HashSet<string>? hits;
    private string? loggingId;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(ShowPeriods))]
    private bool showTree;

    [ObservableProperty]
    private bool isTreeEmpty;

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
        GoalList goals, TaskList tasks, ISettingsStore settings, IStrings strings, TimeProvider time, Func<bool> motionReduced, Action<Action> runOnUi)
    {
        this.goals = goals;
        this.tasks = tasks;
        this.settings = settings;
        this.strings = strings;
        this.time = time;
        this.motionReduced = motionReduced;
        Editor = new GoalEditorViewModel(goals, strings, Today);
        Editor.PropertyChanged += (_, change) =>
        {
            if (change.PropertyName == nameof(GoalEditorViewModel.IsOpen))
            {
                OnPropertyChanged(nameof(ShowList));
            }
        };
        goals.Changed += (_, _) => runOnUi(Refresh);
        tasks.Changed += (_, _) => runOnUi(Refresh);
        Refresh();
    }

    /// <summary>A shown goal just became a hit: time for confetti.</summary>
    public event EventHandler? Celebrate;

    public GoalEditorViewModel Editor { get; }

    public ObservableCollection<GoalSectionViewModel> Sections { get; } = [];

    /// <summary>The shown goals, each under the goal it serves when that one is shown too.</summary>
    public ObservableCollection<GoalRowViewModel> Tree { get; } = [];

    public bool ShowPeriods => !ShowTree;

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

    /// <summary>This week's goals with where they stand, for Today's folded section.</summary>
    public static IReadOnlyList<GoalRowViewModel> ThisWeek(GoalList goals, TaskList tasks, DateOnly today, IStrings strings)
    {
        var week = GoalRules.PeriodStart(GoalHorizon.Week, today);
        var all = goals.All();
        var progress = ProgressOf(goals.Entries(), tasks.All());
        return [.. all.Where(goal => goal.Horizon == GoalHorizon.Week && goal.PeriodStart == week && goal.Status != GoalRules.Dropped)
            .Select(goal => new GoalRowViewModel(goal, progress(goal), null, 0, strings))];
    }

    public void Refresh()
    {
        var today = Today();
        var all = goals.All();
        var byId = all.ToDictionary(goal => goal.Id, StringComparer.Ordinal);
        var progress = ProgressOf(goals.Entries(), tasks.All());
        GoalRowViewModel Row(GoalItem goal, int depth = 0) =>
            new(goal, progress(goal), goal.ParentId is { } id && byId.TryGetValue(id, out var parent) ? parent.Title : null, depth, strings, this);
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
        var shown = new List<GoalItem>();
        foreach (var (horizon, start, name) in periods)
        {
            var own = all.Where(goal => Kept(goal, horizon, start)).ToList();
            shown.AddRange(own);
            // A new week, month or year with no goals yet can start from the last one's (story 35).
            var previous = GoalRules.PeriodStart(horizon, start.AddDays(-1));
            var canCopy = own.Count == 0 && horizon != GoalHorizon.Day && all.Any(goal => Kept(goal, horizon, previous));
            Sections.Add(new GoalSectionViewModel(
                horizon,
                start,
                Upper(strings.Get("Goals.Section", name, PeriodText(horizon, start, strings))),
                [.. own.Select(goal => Row(goal))],
                canCopy,
                strings.Get("Goals.Copy" + horizon),
                section => Editor.OpenNew(section.Horizon, section.Start),
                section => goals.CopyPrevious(section.Horizon, section.Start)));
        }

        var shownIds = shown.Select(goal => goal.Id).ToHashSet(StringComparer.Ordinal);
        var children = shown.Where(goal => goal.ParentId is { } id && shownIds.Contains(id)).ToLookup(goal => goal.ParentId!, StringComparer.Ordinal);
        Tree.Clear();
        void Walk(GoalItem goal, int depth)
        {
            Tree.Add(Row(goal, depth));
            foreach (var child in children[goal.Id])
            {
                Walk(child, depth + 1);
            }
        }

        foreach (var root in shown.Where(goal => goal.ParentId is not { } id || !shownIds.Contains(id)))
        {
            Walk(root, 0);
        }

        IsTreeEmpty = Tree.Count == 0;

        // Confetti for a goal that became a hit since the last look (design spec, level 3).
        var nowHits = Sections.SelectMany(section => section.Rows).Where(row => row.IsHit).Select(row => row.Id).ToHashSet(StringComparer.Ordinal);
        var before = hits;
        hits = nowHits;
        if (before is not null && !nowHits.IsSubsetOf(before) && !motionReduced())
        {
            Celebrate?.Invoke(this, EventArgs.Empty);
        }
    }

    internal void Edit(GoalItem goal) => Editor.OpenEdit(goal);

    internal void SetStatus(string id, string status) => goals.SetStatus(id, status);

    internal void Delete(string id) => goals.Delete(id);

    internal void StartLog(GoalItem goal)
    {
        loggingId = goal.Id;
        LogTitle = strings.Get("Goals.LogTitle", goal.Title);
        LogUnit = goal.Unit ?? string.Empty;
        LogText = string.Empty;
        IsLogging = true;
    }

    [RelayCommand]
    private void ToggleTree() => ShowTree = !ShowTree;

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

    private DateOnly Today() => PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);

    private static Func<GoalItem, GoalProgress> ProgressOf(IEnumerable<GoalEntryItem> entries, IEnumerable<TaskItem> taskList)
    {
        var entriesByGoal = entries.ToLookup(entry => entry.GoalId, StringComparer.Ordinal);
        var tasksByGoal = taskList.Where(task => task.GoalId is not null).ToLookup(task => task.GoalId!, StringComparer.Ordinal);
        return goal => GoalRules.Progress(goal.Mode, goal.Status, goal.Target, tasksByGoal[goal.Id], entriesByGoal[goal.Id]);
    }

    private static string Upper(string text) => text.ToUpper(CultureInfo.CurrentUICulture);
}
