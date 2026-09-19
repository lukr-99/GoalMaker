using System.Globalization;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One goal on the Goals page or Today: its ring, where it stands in words, the goal it serves, and
/// its actions. <see cref="Depth"/> indents it in the cascade.
/// </summary>
public sealed class GoalRowViewModel
{
    private readonly GoalsViewModel? owner;

    public GoalRowViewModel(GoalItem goal, GoalProgress progress, string? parentTitle, int depth, IStrings strings, GoalsViewModel? owner = null)
    {
        this.owner = owner;
        Goal = goal;
        Progress = progress;
        Depth = depth;
        Serves = parentTitle is null ? string.Empty : strings.Get("Goals.Serves", parentTitle);
        ProgressText = Describe(goal, progress, strings);
        RingText = goal.Emoji ?? (goal.Mode == GoalRules.ModeDone || progress.Hit ? string.Empty : progress.Fraction.ToString("P0", CultureInfo.CurrentCulture));
        EditCommand = new RelayCommand(() => owner?.Edit(Goal));
        LogCommand = new RelayCommand(() => owner?.StartLog(Goal));
        MarkDoneCommand = new RelayCommand(() => owner?.SetStatus(Goal.Id, GoalRules.Done));
        ReopenCommand = new RelayCommand(() => owner?.SetStatus(Goal.Id, GoalRules.Open));
        DropCommand = new RelayCommand(() => owner?.SetStatus(Goal.Id, GoalRules.Dropped));
        DeleteCommand = new RelayCommand(() => owner?.Delete(Goal.Id));
    }

    public GoalItem Goal { get; }

    public GoalProgress Progress { get; }

    public string Id => Goal.Id;

    public string Title => Goal.Title;

    /// <summary>The emoji, or the percentage for a goal measured by tasks or a number; empty for a check or nothing.</summary>
    public string RingText { get; }

    public double Fraction => Progress.Fraction;

    public bool IsHit => Progress.Hit;

    /// <summary>The ring shows a check: a hit without an emoji.</summary>
    public bool ShowsCheck => Progress.Hit && Goal.Emoji is null;

    public string ProgressText { get; }

    public string Serves { get; }

    public bool HasServes => Serves.Length > 0;

    public int Depth { get; }

    /// <summary>The indent for the cascade, in pixels.</summary>
    public double Indent => Depth * 28;

    public bool IsOpen => Goal.Status == GoalRules.Open;

    public bool IsClosed => !IsOpen;

    public bool IsDoneOrNot => Goal.Mode == GoalRules.ModeDone;

    public bool CanLog => Goal.Mode == GoalRules.ModeNumber && IsOpen;

    /// <summary>A done-or-not goal's box: checking it marks the goal done, unchecking opens it again.</summary>
    public bool IsDone
    {
        get => Goal.Status == GoalRules.Done;
        set => owner?.SetStatus(Goal.Id, value ? GoalRules.Done : GoalRules.Open);
    }

    public IRelayCommand EditCommand { get; }

    public IRelayCommand LogCommand { get; }

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

    /// <summary>An amount as people write it here: "12.5", no ".0" on whole numbers.</summary>
    public static string Amount(double value) => value.ToString("0.##", CultureInfo.CurrentCulture);
}
