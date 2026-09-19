using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// Adds or edits a goal on the Goals page: name and emoji, horizon and period, how progress is
/// measured (a number needs a target), and the longer goal it serves. Save refuses what the goal list
/// refuses and says why.
/// </summary>
public sealed partial class GoalEditorViewModel : ObservableObject
{
    private readonly GoalList goals;
    private readonly IStrings strings;
    private readonly Func<DateOnly> today;
    private string? goalId;
    private DateOnly? ownStart;
    private bool loading;

    [ObservableProperty]
    private bool isOpen;

    [ObservableProperty]
    private string heading = string.Empty;

    [ObservableProperty]
    private string title = string.Empty;

    [ObservableProperty]
    private string emoji = string.Empty;

    [ObservableProperty]
    private ChoiceViewModel? horizon;

    [ObservableProperty]
    private IReadOnlyList<ChoiceViewModel> periods = [];

    [ObservableProperty]
    private ChoiceViewModel? period;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsNumber))]
    private ChoiceViewModel? mode;

    [ObservableProperty]
    private string targetText = string.Empty;

    [ObservableProperty]
    private string unit = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasParents))]
    private IReadOnlyList<ChoiceViewModel> parents = [];

    [ObservableProperty]
    private ChoiceViewModel? parent;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasError))]
    private string error = string.Empty;

    [ObservableProperty]
    private bool canDelete;

    public GoalEditorViewModel(GoalList goals, IStrings strings, Func<DateOnly> today)
    {
        this.goals = goals;
        this.strings = strings;
        this.today = today;
        Horizons = [.. new[] { GoalHorizon.Year, GoalHorizon.Month, GoalHorizon.Week, GoalHorizon.Day }
            .Select(choice => new ChoiceViewModel(GoalRules.Id(choice), strings.Get("Goals.Horizon" + choice)))];
        EmojiChoices = EmojiPalette.Choices(emoji => Emoji = Emoji == emoji ? string.Empty : emoji);
        Modes =
        [
            new ChoiceViewModel(GoalRules.ModeDone, strings.Get("Goals.ModeDone")),
            new ChoiceViewModel(GoalRules.ModeTasks, strings.Get("Goals.ModeTasks")),
            new ChoiceViewModel(GoalRules.ModeNumber, strings.Get("Goals.ModeNumber")),
        ];
    }

    /// <summary>The emoji to click instead of typing one (docs/goals.md).</summary>
    public IReadOnlyList<EmojiChoiceViewModel> EmojiChoices { get; }

    public IReadOnlyList<ChoiceViewModel> Horizons { get; }

    public IReadOnlyList<ChoiceViewModel> Modes { get; }

    public bool IsNumber => Mode?.Id == GoalRules.ModeNumber;

    /// <summary>Whether any goal could be this one's parent; the picker hides otherwise.</summary>
    public bool HasParents => Parents.Count > 1;

    public bool HasError => Error.Length > 0;

    /// <summary>A new goal for the <paramref name="horizonValue"/> period starting on <paramref name="start"/>.</summary>
    public void OpenNew(GoalHorizon horizonValue, DateOnly start) => Open(null, new GoalItem(string.Empty, string.Empty, horizonValue, start));

    public void OpenEdit(GoalItem goal) => Open(goal.Id, goal);

    partial void OnHorizonChanged(ChoiceViewModel? value)
    {
        if (!loading)
        {
            ownStart = null;
            ShowPeriods(GoalRules.PeriodStart(Selected(), today()));
        }
    }

    partial void OnPeriodChanged(ChoiceViewModel? value)
    {
        if (!loading)
        {
            ShowParents(Parent?.Id);
        }
    }

    partial void OnTitleChanged(string value) => Error = string.Empty;

    partial void OnTargetTextChanged(string value) => Error = string.Empty;

    [RelayCommand]
    private void Save()
    {
        var draft = new GoalDraft(
            Title,
            Selected(),
            Start(),
            Mode?.Id ?? GoalRules.ModeDone,
            Emoji,
            Parent?.Id,
            ParseAmount(TargetText),
            Unit);
        var saved = goalId is null ? goals.Add(draft) is not null : goals.Update(goalId, draft);
        if (saved)
        {
            IsOpen = false;
        }
        else
        {
            Error = strings.Get("Goals.Invalid");
        }
    }

    [RelayCommand]
    private void Cancel() => IsOpen = false;

    [RelayCommand]
    private void Delete()
    {
        if (goalId is { } id)
        {
            goals.Delete(id);
        }

        IsOpen = false;
    }

    /// <summary>"5", "5.5" or "5,5"; null when it isn't a number.</summary>
    internal static double? ParseAmount(string text) =>
        double.TryParse(text.Trim().Replace(',', '.'), NumberStyles.Float, CultureInfo.InvariantCulture, out var value) && double.IsFinite(value) ? value : null;

    private void Open(string? id, GoalItem goal)
    {
        loading = true;
        try
        {
            goalId = id;
            ownStart = id is null ? null : goal.PeriodStart;
            Heading = strings.Get(id is null ? "Goals.New" : "Goals.Edit");
            Title = goal.Title;
            Emoji = goal.Emoji ?? string.Empty;
            Horizon = Horizons.First(choice => choice.Id == GoalRules.Id(goal.Horizon));
            Mode = Modes.FirstOrDefault(choice => choice.Id == goal.Mode) ?? Modes[0];
            TargetText = goal.Target is { } target ? target.ToString("0.##", CultureInfo.CurrentCulture) : string.Empty;
            Unit = goal.Unit ?? string.Empty;
            CanDelete = id is not null;
            ShowPeriods(goal.PeriodStart);
            ShowParents(goal.ParentId);
            Error = string.Empty;
        }
        finally
        {
            loading = false;
        }

        IsOpen = true;
    }

    // This period and the next of the chosen horizon, and the goal's own period when it is another one.
    private void ShowPeriods(DateOnly selected)
    {
        var horizonValue = Selected();
        var current = GoalRules.PeriodStart(horizonValue, today());
        var next = GoalRules.PeriodEnd(horizonValue, current).AddDays(1);
        var starts = new List<DateOnly> { current, next };
        if (ownStart is { } own && GoalRules.PeriodStart(horizonValue, own) == own && !starts.Contains(own))
        {
            starts.Add(own);
        }

        var wasLoading = loading;
        loading = true;
        Periods = [.. starts.Select(start => new ChoiceViewModel(
            start.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture),
            start == current ? strings.Get("Goals.This" + horizonValue) : start == next ? strings.Get("Goals.Next" + horizonValue) : GoalsViewModel.PeriodText(horizonValue, start, strings)))];
        Period = Periods.FirstOrDefault(choice => choice.Id == selected.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture)) ?? Periods[0];
        loading = wasLoading;
        ShowParents(Parent?.Id);
    }

    // The goals this one can serve: a longer horizon whose period overlaps (docs/goals.md).
    private void ShowParents(string? keep)
    {
        var horizonValue = Selected();
        var start = Start();
        Parents = [new ChoiceViewModel(null, strings.Get("Goals.NoParent")), .. goals.All()
            .Where(goal => goal.Id != goalId && goal.Status != GoalRules.Dropped && GoalRules.CanServe(horizonValue, start, goal.Horizon, goal.PeriodStart))
            .Select(goal => new ChoiceViewModel(goal.Id, goal.Emoji is { } emoji ? $"{emoji} {goal.Title}" : goal.Title))];
        Parent = Parents.FirstOrDefault(choice => choice.Id == keep) ?? Parents[0];
    }

    private GoalHorizon Selected() => GoalRules.HorizonOf(Horizon?.Id) ?? GoalHorizon.Week;

    private DateOnly Start() => Period?.Id is { } id ? DateOnly.ParseExact(id, "yyyy-MM-dd", CultureInfo.InvariantCulture) : GoalRules.PeriodStart(Selected(), today());
}
