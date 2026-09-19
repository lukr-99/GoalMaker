using System.Collections.ObjectModel;
using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.App.Shell;
using GoalMaker.App.Startup;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The Plan tomorrow ritual (docs/plan-tomorrow.md): step 1 decides on what's left from today, step 2
/// sets up tomorrow and its top priorities, then a summary. Decisions are saved at once; what each
/// task shows is read back from its state, so changes from another device show up too.
/// </summary>
public sealed partial class PlanViewModel : ObservableObject
{
    private readonly TaskList tasks;
    private readonly AreaList areas;
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly TimeProvider time;
    private readonly Func<string, Brush?> areaBrush;
    private readonly TickSound tick;
    private readonly Action<AppPage> openPage;
    private readonly Action<DateOnly>? finished;

    // Every task step 1 has asked about, in the order it first appeared, so decided rows stay put.
    private readonly List<string> reviewed = [];
    private readonly HashSet<string> seen = new(StringComparer.Ordinal);
    private DateOnly today;
    private int priorities;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsTodayStep), nameof(IsTomorrowStep), nameof(IsDoneStep))]
    [NotifyCanExecuteChangedFor(nameof(NextCommand), nameof(BackCommand))]
    private PlanStep step;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(NextCommand))]
    private int undecided;

    [ObservableProperty]
    private string stepText = string.Empty;

    [ObservableProperty]
    private string todayIntro = string.Empty;

    [ObservableProperty]
    private string tomorrowIntro = string.Empty;

    [ObservableProperty]
    private bool hasInbox;

    [ObservableProperty]
    private string inboxHeader = string.Empty;

    [ObservableProperty]
    private bool isInboxExpanded;

    [ObservableProperty]
    private string tomorrowCount = "0";

    [ObservableProperty]
    private string tomorrowCountLabel = string.Empty;

    [ObservableProperty]
    private string prioritiesText = string.Empty;

    [ObservableProperty]
    private string outcomeText = string.Empty;

    public PlanViewModel(
        TaskList tasks,
        AreaList areas,
        ComposerViewModel composer,
        ISettingsStore settings,
        IStrings strings,
        TimeProvider time,
        Func<string, Brush?> areaBrush,
        TickSound tick,
        Action<AppPage> openPage,
        Action<Action> runOnUi,
        Action<DateOnly>? finished = null)
    {
        this.tasks = tasks;
        this.areas = areas;
        this.settings = settings;
        this.strings = strings;
        this.time = time;
        this.areaBrush = areaBrush;
        this.tick = tick;
        this.openPage = openPage;
        this.finished = finished;
        Composer = composer;
        tasks.Changed += (_, _) => runOnUi(Refresh);
        areas.Changed += (_, _) => runOnUi(Refresh);
        Start();
    }

    public ComposerViewModel Composer { get; }

    /// <summary>Step 1: today's and earlier tasks, each with its decision.</summary>
    public ObservableCollection<ReviewRowViewModel> Review { get; } = [];

    /// <summary>Step 2: tomorrow's tasks by time, flags to pick top priorities.</summary>
    public ObservableCollection<PlanTaskViewModel> Tomorrow { get; } = [];

    /// <summary>Step 2: the Inbox, to bring tasks into tomorrow.</summary>
    public ObservableCollection<PlanTaskViewModel> Inbox { get; } = [];

    public bool IsTodayStep => Step == PlanStep.Today;

    public bool IsTomorrowStep => Step == PlanStep.Tomorrow;

    public bool IsDoneStep => Step == PlanStep.Done;

    /// <summary>Starts over at step 1 on the planning day it is now.</summary>
    public void Start()
    {
        today = PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);
        reviewed.Clear();
        seen.Clear();
        Review.Clear();
        IsInboxExpanded = false;
        Step = PlanStep.Today;
        Refresh();
    }

    /// <summary>Reads the tasks again; rows are updated in place so nothing jumps.</summary>
    public void Refresh()
    {
        var all = tasks.All();
        foreach (var task in PlanRules.Review(all, today))
        {
            if (seen.Add(task.Id))
            {
                reviewed.Add(task.Id);
            }
        }

        var byId = all.ToDictionary(task => task.Id, StringComparer.Ordinal);
        var areaById = areas.All().ToDictionary(area => area.Id, StringComparer.Ordinal);
        AreaItem? AreaOf(TaskItem task) => task.AreaId is { } id ? areaById.GetValueOrDefault(id) : null;
        Brush? BrushOf(AreaItem? area) => area is null ? null : areaBrush(area.ColorId);

        priorities = PlanRules.Priorities(all, today);
        var canPick = priorities < PlanRules.MaxPriorities;
        Sync(
            Review,
            [.. reviewed.Select(byId.GetValueOrDefault).OfType<TaskItem>()],
            row => row.Item.Id,
            task => new ReviewRowViewModel(task, AreaOf(task), BrushOf(AreaOf(task)), today, strings, Decide),
            (row, task) => row.Update(task, AreaOf(task), BrushOf(AreaOf(task))));
        Sync(
            Tomorrow,
            PlanRules.Tomorrow(all, today),
            row => row.Item.Id,
            task => new PlanTaskViewModel(task, AreaOf(task), BrushOf(AreaOf(task)), canPick, strings, TogglePriority, PlanForTomorrow),
            (row, task) => row.Update(task, AreaOf(task), BrushOf(AreaOf(task)), canPick));
        Sync(
            Inbox,
            ListRules.Lists(all, today).Inbox,
            row => row.Item.Id,
            task => new PlanTaskViewModel(task, null, null, canPick, strings, TogglePriority, PlanForTomorrow),
            (row, task) => row.Update(task, null, null, canPick));

        Undecided = Review.Count(row => row.Decision == PlanDecision.Undecided);
        ShowTexts();
    }

    partial void OnStepChanged(PlanStep value) => ShowTexts();

    private bool CanGoNext() => Step == PlanStep.Tomorrow || (Step == PlanStep.Today && Undecided == 0);

    [RelayCommand(CanExecute = nameof(CanGoNext))]
    private void Next()
    {
        var before = Step;
        Step = before == PlanStep.Today ? PlanStep.Tomorrow : PlanStep.Done;

        // Reaching the end counts as the day's run, which quiets the evening reminder everywhere.
        if (before == PlanStep.Tomorrow)
        {
            finished?.Invoke(today);
        }
    }

    private bool CanGoBack() => Step == PlanStep.Tomorrow;

    [RelayCommand(CanExecute = nameof(CanGoBack))]
    private void Back() => Step = PlanStep.Today;

    [RelayCommand]
    private void Close() => openPage(AppPage.Today);

    private void Decide(ReviewRowViewModel row, PlanDecision decision, DateOnly? day)
    {
        switch (decision)
        {
            case PlanDecision.Tomorrow:
                tasks.Plan(row.Item.Id, today.AddDays(1));
                break;
            case PlanDecision.Later when day is { } later && later > today:
                tasks.Plan(row.Item.Id, later);
                break;
            case PlanDecision.Done:
                if (row.Decision != PlanDecision.Done && settings.Appearance.CompletionSound)
                {
                    tick.Play();
                }

                tasks.SetDone(row.Item.Id, true);
                break;
            case PlanDecision.Dropped:
                tasks.Drop(row.Item.Id);
                break;
        }
    }

    // Flagging stops at three; clearing always works (docs/plan-tomorrow.md).
    private void TogglePriority(PlanTaskViewModel row)
    {
        if (!row.TopPriority && priorities >= PlanRules.MaxPriorities)
        {
            return;
        }

        tasks.SetTopPriority(row.Item.Id, !row.TopPriority);
    }

    private void PlanForTomorrow(PlanTaskViewModel row) => tasks.Plan(row.Item.Id, today.AddDays(1));

    private void ShowTexts()
    {
        StepText = Step switch
        {
            PlanStep.Today when Undecided == 0 => strings.Get("Plan.StepTodayDone"),
            PlanStep.Today => strings.Get("Plan.StepToday", Undecided),
            PlanStep.Tomorrow => strings.Get("Plan.StepTomorrow", priorities, PlanRules.MaxPriorities),
            _ => strings.Get("Plan.StepDone"),
        };
        TodayIntro = strings.Get(Review.Count == 0 ? "Plan.TodayClear" : "Plan.TodayIntro");
        TomorrowIntro = Tomorrow.Count == 0 ? strings.Get("Plan.TomorrowEmpty") : strings.Get("Plan.TomorrowIntro", PlanRules.MaxPriorities);
        HasInbox = Inbox.Count > 0;
        InboxHeader = strings.Get("Plan.FromInbox", Inbox.Count).ToUpper(System.Globalization.CultureInfo.CurrentUICulture);
        TomorrowCount = Tomorrow.Count.ToString(System.Globalization.CultureInfo.CurrentCulture);
        TomorrowCountLabel = strings.Get(Tomorrow.Count == 1 ? "Plan.DoneTask" : "Plan.DoneTasks");
        PrioritiesText = priorities switch
        {
            0 => strings.Get("Plan.DoneNoPriorities"),
            1 => strings.Get("Plan.DonePriority", priorities),
            _ => strings.Get("Plan.DonePriorities", priorities),
        };
        string? Part(PlanDecision decision, string key) =>
            Review.Count(row => row.Decision == decision) is var count and > 0 ? strings.Get(key, count) : null;
        var parts = new[]
        {
            Part(PlanDecision.Tomorrow, "Plan.OutcomeTomorrow"),
            Part(PlanDecision.Later, "Plan.OutcomeLater"),
            Part(PlanDecision.Done, "Plan.OutcomeDone"),
            Part(PlanDecision.Dropped, "Plan.OutcomeDropped"),
        }.OfType<string>().ToList();
        OutcomeText = parts.Count == 0 ? strings.Get("Plan.OutcomeNone") : strings.Get("Plan.Outcome", string.Join(" · ", parts));
    }

    // Brings a collection to the given order, reusing rows by task id so their controls stay put.
    private static void Sync<TRow>(
        ObservableCollection<TRow> rows,
        IReadOnlyList<TaskItem> items,
        Func<TRow, string> idOf,
        Func<TaskItem, TRow> create,
        Action<TRow, TaskItem> update)
        where TRow : class
    {
        var existing = rows.ToDictionary(idOf, StringComparer.Ordinal);
        for (var index = 0; index < items.Count; index++)
        {
            var item = items[index];
            if (existing.Remove(item.Id, out var row))
            {
                var at = rows.IndexOf(row);
                if (at != index)
                {
                    rows.Move(at, index);
                }

                update(row, item);
            }
            else
            {
                rows.Insert(index, create(item));
            }
        }

        foreach (var gone in existing.Values)
        {
            rows.Remove(gone);
        }
    }
}
