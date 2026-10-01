using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One goal on the Goals page or Today: its ring, big number and bar, where it stands in words and
/// against its period (<see cref="Standing"/>), the goal it feeds, its quick log and its actions. On the
/// Goals page a click picks it, and <see cref="IsLit"/> and <see cref="IsDimmed"/> follow the lit chain.
/// </summary>
public sealed partial class GoalRowViewModel : ObservableObject
{
    private readonly GoalsViewModel? owner;
    private readonly IStrings strings;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(ChainBorder), nameof(Status))]
    private bool isPicked;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(ChainBorder), nameof(Status))]
    private bool isLit;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(CardOpacity))]
    private bool isDimmed;

    public GoalRowViewModel(
        GoalItem goal,
        GoalProgress progress,
        string? parentTitle,
        IStrings strings,
        GoalsViewModel? owner = null,
        GoalStanding? standing = null,
        double? quickAmount = null)
    {
        this.owner = owner;
        this.strings = strings;
        Goal = goal;
        Progress = progress;
        Standing = standing ?? new GoalStanding(GoalPace.OnTrack);
        QuickAmount = goal.Mode == GoalRules.ModeNumber ? quickAmount : null;
        Feeds = parentTitle is null ? string.Empty : strings.Get("Goals.Feeds", parentTitle);
        ProgressText = Describe(goal, progress, strings);
        PaceText = PaceWords(goal, Standing, strings);
        RingText = goal.Emoji ?? (goal.Mode == GoalRules.ModeDone || progress.Hit ? string.Empty : progress.Fraction.ToString("P0", CultureInfo.CurrentCulture));
        (OfText, Counts) = goal.Mode switch
        {
            GoalRules.ModeTasks when progress.Target > 0 => (strings.Get((int)progress.Target == 1 ? "Goals.OfTask" : "Goals.OfTasks", (int)progress.Target), true),
            GoalRules.ModeNumber when goal.Unit is { } unit => (strings.Get("Goals.OfUnit", Amount(progress.Target), unit), true),
            GoalRules.ModeNumber => (strings.Get("Goals.Of", Amount(progress.Target)), true),
            _ => (string.Empty, false),
        };
        QuickText = QuickAmount is { } amount
            ? goal.Unit is { } quickUnit ? strings.Get("Goals.QuickUnit", Amount(amount), quickUnit) : strings.Get("Goals.Quick", Amount(amount))
            : strings.Get("Goals.LogShort");
        QuickName = QuickAmount is null ? strings.Get("Goals.LogOn", goal.Title) : strings.Get("Goals.QuickName", QuickText, goal.Title);
        CardName = strings.Get("Goals.CardName", DisplayTitle, ProgressText, PaceText);
        EditCommand = new RelayCommand(() => owner?.Edit(Goal));
        LogCommand = new RelayCommand(() => owner?.StartLog(Goal));
        QuickLogCommand = new RelayCommand(() => owner?.QuickLog(this));
        PickCommand = new RelayCommand(() => owner?.Pick(Id));
        MarkDoneCommand = new RelayCommand(() => owner?.SetStatus(Goal.Id, GoalRules.Done));
        ReopenCommand = new RelayCommand(() => owner?.SetStatus(Goal.Id, GoalRules.Open));
        DropCommand = new RelayCommand(() => owner?.SetStatus(Goal.Id, GoalRules.Dropped));
        DeleteCommand = new RelayCommand(() => owner?.Delete(Goal.Id));
    }

    public GoalItem Goal { get; }

    public GoalProgress Progress { get; }

    /// <summary>Behind, on track, hit or dropped against the share of its period gone by (docs/goals.md).</summary>
    public GoalStanding Standing { get; }

    public GoalPace Pace => Standing.Pace;

    /// <summary>What the quick log adds on a numeric goal, the latest amount logged by hand; null asks for one.</summary>
    public double? QuickAmount { get; }

    public string Id => Goal.Id;

    public string Title => Goal.Title;

    /// <summary>The title with the emoji in front, as the card shows it.</summary>
    public string DisplayTitle => Goal.Emoji is { } emoji ? $"{emoji} {Goal.Title}" : Goal.Title;

    /// <summary>The emoji, or the percentage for a goal measured by tasks or a number; empty for a check or nothing.</summary>
    public string RingText { get; }

    public double Fraction => Progress.Fraction;

    public bool IsHit => Progress.Hit;

    /// <summary>The ring shows a check: a hit without an emoji.</summary>
    public bool ShowsCheck => Progress.Hit && Goal.Emoji is null;

    public string ProgressText { get; }

    /// <summary>"On track", "Hit", "Needs you", "Behind by 6 km".</summary>
    public string PaceText { get; }

    /// <summary>The big number: what is done so far.</summary>
    public string ValueText => Amount(Progress.Value);

    /// <summary>"of 25 km", "of 5 tasks", beside the big number.</summary>
    public string OfText { get; }

    /// <summary>The card shows the big number and its bar: a number goal, or one with tasks to count.</summary>
    public bool Counts { get; }

    /// <summary>The card says where it stands in words instead: done or not, or no tasks yet.</summary>
    public bool ShowsWords => !Counts;

    public string Feeds { get; }

    public bool HasFeeds => Feeds.Length > 0;

    public bool IsOpen => Goal.Status == GoalRules.Open;

    public bool IsClosed => !IsOpen;

    public bool IsDoneOrNot => Goal.Mode == GoalRules.ModeDone;

    public bool ShowsRing => !IsDoneOrNot;

    public bool CanLog => Goal.Mode == GoalRules.ModeNumber && IsOpen;

    /// <summary>The quick log shows on an open numeric goal that isn't hit yet.</summary>
    public bool CanQuickLog => CanLog && !Progress.Hit;

    /// <summary>"+5 km", or "Log" when nothing was logged yet.</summary>
    public string QuickText { get; }

    public string QuickName { get; }

    /// <summary>What a screen reader says for the card: its name, where it stands and its pace.</summary>
    public string CardName { get; }

    /// <summary>The card's chain state for a screen reader: picked, in the lit chain, or nothing.</summary>
    public string Status => IsPicked ? strings.Get("Goals.Picked") : IsLit ? strings.Get("Goals.InChain") : string.Empty;

    /// <summary>The accent edge: thicker on the picked card, thinner on the rest of its chain.</summary>
    public double ChainBorder => IsPicked ? 3 : IsLit ? 2 : 0;

    /// <summary>Cards outside a lit chain fade back.</summary>
    public double CardOpacity => IsDimmed ? 0.32 : 1;

    /// <summary>A done-or-not goal's box: checking it marks the goal done, unchecking opens it again.</summary>
    public bool IsDone
    {
        get => Goal.Status == GoalRules.Done;
        set => owner?.SetStatus(Goal.Id, value ? GoalRules.Done : GoalRules.Open);
    }

    public IRelayCommand EditCommand { get; }

    public IRelayCommand LogCommand { get; }

    public IRelayCommand QuickLogCommand { get; }

    public IRelayCommand PickCommand { get; }

    public IRelayCommand MarkDoneCommand { get; }

    public IRelayCommand ReopenCommand { get; }

    public IRelayCommand DropCommand { get; }

    public IRelayCommand DeleteCommand { get; }

    /// <summary>Where a goal stands in words: "Tasks done: 2 of 5", "12 of 50 km", "Done".</summary>
    public static string Describe(GoalItem goal, GoalProgress progress, IStrings strings) => goal.Mode switch
    {
        GoalRules.ModeTasks when progress.Target <= 0 => strings.Get("Goals.NoTasks"),
        GoalRules.ModeTasks => strings.Get("Goals.TasksDone", (int)progress.Value, (int)progress.Target),
        GoalRules.ModeNumber when goal.Unit is { } unit => strings.Get("Goals.AmountUnit", Amount(progress.Value), Amount(progress.Target), unit),
        GoalRules.ModeNumber => strings.Get("Goals.Amount", Amount(progress.Value), Amount(progress.Target)),
        _ => strings.Get(goal.Status == GoalRules.Done ? "Goals.StateDone" : "Goals.StateOpen"),
    };

    /// <summary>A goal's pace in words: "On track", "Hit", "Needs you", "Behind by 6 km".</summary>
    public static string PaceWords(GoalItem goal, GoalStanding standing, IStrings strings) => standing switch
    {
        { Pace: GoalPace.Hit } => strings.Get("Goals.PaceHit"),
        { Pace: GoalPace.Dropped } => strings.Get("Goals.PaceDropped"),
        { Pace: GoalPace.OnTrack } => strings.Get("Goals.PaceOnTrack"),
        { Behind: null } => strings.Get("Goals.PaceNeedsYou"),
        { Behind: { } behind } when goal.Mode == GoalRules.ModeTasks => strings.Get((int)behind == 1 ? "Goals.PaceBehindTask" : "Goals.PaceBehindTasks", (int)behind),
        { Behind: { } behind } when goal.Unit is { } unit => strings.Get("Goals.PaceBehindUnit", Amount(behind), unit),
        { Behind: { } behind } => strings.Get("Goals.PaceBehind", Amount(behind)),
    };

    /// <summary>An amount as people write it here: "12.5", no ".0" on whole numbers.</summary>
    public static string Amount(double value) => value.ToString("0.##", CultureInfo.CurrentCulture);
}
