using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// Adds or edits a habit on the Habits page: name and emoji, how often it runs (weekdays get their own
/// boxes, a week or a month a number of times), how it is measured, and the goal it serves. Save refuses
/// what the habit list refuses and says why.
/// </summary>
public sealed partial class HabitEditorViewModel : ObservableObject
{
    private readonly HabitList habits;
    private readonly GoalList goals;
    private readonly IStrings strings;
    private readonly Func<DateOnly> today;
    private string? habitId;
    private DateOnly startsOn;

    [ObservableProperty]
    private bool isOpen;

    [ObservableProperty]
    private string heading = string.Empty;

    [ObservableProperty]
    private string name = string.Empty;

    [ObservableProperty]
    private string emoji = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsWeekdays), nameof(IsTimes), nameof(TimesLabel))]
    private ChoiceViewModel? cadence;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(TimesLabel))]
    private int times = 3;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsCounted))]
    private ChoiceViewModel? measure;

    [ObservableProperty]
    private string targetText = string.Empty;

    [ObservableProperty]
    private string unit = string.Empty;

    [ObservableProperty]
    private IReadOnlyList<ChoiceViewModel> goalChoices = [];

    [ObservableProperty]
    private ChoiceViewModel? goal;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasError))]
    private string error = string.Empty;

    [ObservableProperty]
    private bool canDelete;

    public HabitEditorViewModel(HabitList habits, GoalList goals, IStrings strings, Func<DateOnly> today)
    {
        this.habits = habits;
        this.goals = goals;
        this.strings = strings;
        this.today = today;
        startsOn = today();
        Cadences =
        [
            new ChoiceViewModel(HabitRules.Daily, strings.Get("Habits.CadenceDaily")),
            new ChoiceViewModel(HabitRules.OnWeekdays, strings.Get("Habits.CadenceWeekdays")),
            new ChoiceViewModel(HabitRules.PerWeek, strings.Get("Habits.CadencePerWeek")),
            new ChoiceViewModel(HabitRules.PerMonth, strings.Get("Habits.CadencePerMonth")),
        ];
        Measures =
        [
            new ChoiceViewModel(HabitRules.Check, strings.Get("Habits.MeasureCheck")),
            new ChoiceViewModel(HabitRules.Count, strings.Get("Habits.MeasureCount")),
            new ChoiceViewModel(HabitRules.Amount, strings.Get("Habits.MeasureAmount")),
        ];
        Days = [.. Enumerable.Range(0, 7).Select(day => new HabitDayViewModel(
            day,
            CultureInfo.CurrentCulture.DateTimeFormat.AbbreviatedDayNames[(day + 1) % 7],
            () => Error = string.Empty))];
        EmojiChoices = EmojiPalette.Choices(emoji => Emoji = Emoji == emoji ? string.Empty : emoji);
        Cadence = Cadences[0];
        Measure = Measures[0];
    }

    /// <summary>The emoji to click instead of typing one (docs/habits.md).</summary>
    public IReadOnlyList<EmojiChoiceViewModel> EmojiChoices { get; }

    public IReadOnlyList<ChoiceViewModel> Cadences { get; }

    public IReadOnlyList<ChoiceViewModel> Measures { get; }

    /// <summary>Monday to Sunday, for the weekdays cadence.</summary>
    public IReadOnlyList<HabitDayViewModel> Days { get; }

    public bool IsWeekdays => Cadence?.Id == HabitRules.OnWeekdays;

    public bool IsTimes => Cadence?.Id is HabitRules.PerWeek or HabitRules.PerMonth;

    public bool IsCounted => Measure?.Id != HabitRules.Check;

    public bool HasError => Error.Length > 0;

    /// <summary>"3 times a week", beside the stepper.</summary>
    public string TimesLabel => Cadence?.Id == HabitRules.PerMonth
        ? strings.Get("Habits.TimesMonth", Times)
        : strings.Get("Habits.TimesWeek", Times);

    /// <summary>A new habit, starting today.</summary>
    public void OpenNew() => Open(null, new HabitItem(string.Empty, string.Empty, today()));

    public void OpenEdit(HabitItem habit) => Open(habit.Id, habit);

    partial void OnNameChanged(string value) => Error = string.Empty;

    partial void OnTargetTextChanged(string value) => Error = string.Empty;

    [RelayCommand]
    private void Save()
    {
        var cadenceId = Cadence?.Id ?? HabitRules.Daily;
        var mask = Days.Where(day => day.IsChosen).Sum(day => 1 << day.Index);
        var draft = new HabitDraft(Name, startsOn)
        {
            Cadence = cadenceId,
            Weekdays = cadenceId == HabitRules.OnWeekdays ? mask : null,
            Times = IsTimes ? Times : null,
            Measure = Measure?.Id ?? HabitRules.Check,
            Target = GoalEditorViewModel.ParseAmount(TargetText),
            Unit = Unit,
            Emoji = Emoji,
            GoalId = Goal?.Id,
        };
        var saved = habitId is null ? habits.Add(draft) is not null : habits.Update(habitId, draft);
        if (saved)
        {
            IsOpen = false;
        }
        else
        {
            Error = strings.Get("Habits.Invalid");
        }
    }

    [RelayCommand]
    private void Cancel() => IsOpen = false;

    [RelayCommand]
    private void Delete()
    {
        if (habitId is { } id)
        {
            habits.Delete(id);
        }

        IsOpen = false;
    }

    [RelayCommand]
    private void FewerTimes() => Times = Math.Max(1, Times - 1);

    [RelayCommand]
    private void MoreTimes() => Times = Math.Min(Cadence?.Id == HabitRules.PerMonth ? 31 : 7, Times + 1);

    private void Open(string? id, HabitItem habit)
    {
        habitId = id;
        startsOn = id is null ? today() : habit.StartsOn;
        Heading = strings.Get(id is null ? "Habits.New" : "Habits.Edit");
        Name = habit.Name;
        Emoji = habit.Emoji ?? string.Empty;
        Cadence = Cadences.FirstOrDefault(choice => choice.Id == habit.Cadence) ?? Cadences[0];
        Times = habit.Times ?? 3;
        Measure = Measures.FirstOrDefault(choice => choice.Id == habit.Measure) ?? Measures[0];
        TargetText = habit.Target is { } target ? HabitRowViewModel.Amount(target) : string.Empty;
        Unit = habit.Unit ?? string.Empty;
        var mask = habit.Weekdays ?? 31;
        foreach (var day in Days)
        {
            day.Set((mask & (1 << day.Index)) != 0);
        }

        ShowGoals(habit.GoalId);
        CanDelete = id is not null;
        Error = string.Empty;
        IsOpen = true;
    }

    // The goals a habit can serve: not dropped and not over yet, or the one it already serves.
    private void ShowGoals(string? keep)
    {
        var day = today();
        GoalChoices = [new ChoiceViewModel(null, strings.Get("Habits.NoGoal")), .. goals.All()
            .Where(goal => goal.Id == keep || (goal.Status != GoalRules.Dropped && GoalRules.PeriodEnd(goal.Horizon, goal.PeriodStart) >= day))
            .Select(goal => new ChoiceViewModel(goal.Id, goal.Emoji is { } emoji ? $"{emoji} {goal.Title}" : goal.Title))];
        Goal = GoalChoices.FirstOrDefault(choice => choice.Id == keep) ?? GoalChoices[0];
    }
}
