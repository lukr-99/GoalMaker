using System.Globalization;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One habit on the Habits page or Today: its ring, how often it runs, where today stands, the streak,
/// its heatmap and its actions (docs/habits.md).
/// </summary>
public sealed class HabitRowViewModel
{
    private readonly HabitsViewModel? owner;

    public HabitRowViewModel(
        HabitItem habit,
        double? ring,
        int streak,
        double value,
        int met,
        bool skipped,
        bool paused,
        string? goalTitle,
        IReadOnlyList<HabitHeat> heat,
        IStrings strings,
        HabitsViewModel? owner = null)
    {
        this.owner = owner;
        Habit = habit;
        Ring = ring;
        Streak = streak;
        Value = value;
        IsSkipped = skipped;
        IsPaused = paused;
        Heat = heat;
        Serves = goalTitle is null ? string.Empty : strings.Get("Habits.Serves", goalTitle);
        CadenceText = Cadence(habit, strings);
        StatusText = Status(habit, ring, value, met, skipped, paused, strings);
        StreakText = streak <= 0 ? string.Empty : strings.Get(habit.Cadence switch
        {
            HabitRules.PerWeek => "Habits.StreakWeeks",
            HabitRules.PerMonth => "Habits.StreakMonths",
            _ => "Habits.StreakDays",
        }, streak);
        SkipText = strings.Get(habit.Cadence switch
        {
            HabitRules.PerWeek => "Habits.SkipWeek",
            HabitRules.PerMonth => "Habits.SkipMonth",
            _ => "Habits.SkipDay",
        });
        CheckInText = strings.Get("Habits.CheckIn", habit.Name);
        RingText = habit.Emoji ?? string.Empty;
        CheckInCommand = new RelayCommand(() => owner?.Tap(habit));
        EditCommand = new RelayCommand(() => owner?.Edit(habit));
        LogCommand = new RelayCommand(() => owner?.StartLog(habit));
        ClearCommand = new RelayCommand(() => owner?.ClearToday(habit.Id));
        SkipCommand = new RelayCommand(() => owner?.Skip(habit.Id, true));
        UnskipCommand = new RelayCommand(() => owner?.Skip(habit.Id, false));
        PauseCommand = new RelayCommand(() => owner?.Pause(habit.Id));
        ResumeCommand = new RelayCommand(() => owner?.Resume(habit.Id));
        ArchiveCommand = new RelayCommand(() => owner?.SetArchived(habit.Id, true));
        UnarchiveCommand = new RelayCommand(() => owner?.SetArchived(habit.Id, false));
        DeleteCommand = new RelayCommand(() => owner?.Delete(habit.Id));
    }

    public HabitItem Habit { get; }

    public string Id => Habit.Id;

    public string Name => Habit.Name;

    /// <summary>Today's ring, or null when today isn't due; the page shows an empty ring then.</summary>
    public double? Ring { get; }

    public double Fraction => Ring ?? 0;

    public int Streak { get; }

    public double Value { get; }

    public IReadOnlyList<HabitHeat> Heat { get; }

    public bool IsSkipped { get; }

    public bool IsPaused { get; }

    public bool IsArchived => Habit.Archived;

    /// <summary>Today's part is done: the ring is full, or the period is skipped.</summary>
    public bool IsDone => IsSkipped || Fraction >= 1;

    /// <summary>The ring shows a check: done without an emoji.</summary>
    public bool ShowsCheck => IsDone && Habit.Emoji is null;

    /// <summary>The habit takes check-ins today: it isn't archived, paused, or off duty.</summary>
    public bool CanCheckIn => !IsArchived && !IsPaused && Ring is not null;

    public bool CanLog => CanCheckIn && Habit.Measure != HabitRules.Check;

    public bool CanClear => Value > 0;

    public bool CanSkip => !IsArchived && !IsPaused && !IsSkipped;

    public bool CanPause => !IsArchived && !IsPaused;

    public string RingText { get; }

    public string CadenceText { get; }

    public string StatusText { get; }

    public string StreakText { get; }

    public bool HasStreak => StreakText.Length > 0;

    public string SkipText { get; }

    public string CheckInText { get; }

    public string Serves { get; }

    public bool HasServes => Serves.Length > 0;

    public IRelayCommand CheckInCommand { get; }

    public IRelayCommand EditCommand { get; }

    public IRelayCommand LogCommand { get; }

    public IRelayCommand ClearCommand { get; }

    public IRelayCommand SkipCommand { get; }

    public IRelayCommand UnskipCommand { get; }

    public IRelayCommand PauseCommand { get; }

    public IRelayCommand ResumeCommand { get; }

    public IRelayCommand ArchiveCommand { get; }

    public IRelayCommand UnarchiveCommand { get; }

    public IRelayCommand DeleteCommand { get; }

    /// <summary>How often a habit runs: "Every day", "Mon, Wed, Fri", "3 times a week".</summary>
    public static string Cadence(HabitItem habit, IStrings strings) => habit.Cadence switch
    {
        HabitRules.OnWeekdays => Weekdays(habit.Weekdays ?? 0, strings),
        HabitRules.PerWeek => strings.Get("Habits.TimesWeek", habit.Times ?? 1),
        HabitRules.PerMonth => strings.Get("Habits.TimesMonth", habit.Times ?? 1),
        _ => strings.Get("Habits.CadenceDaily"),
    };

    /// <summary>An amount as people write it here: "12.5", no ".0" on whole numbers.</summary>
    public static string Amount(double value) => value.ToString("0.##", CultureInfo.CurrentCulture);

    private static string Weekdays(int mask, IStrings strings) => mask switch
    {
        31 => strings.Get("Habits.Workdays"),
        96 => strings.Get("Habits.Weekend"),
        _ => string.Join(", ", Enumerable.Range(0, 7)
            .Where(day => (mask & (1 << day)) != 0)
            .Select(day => CultureInfo.CurrentCulture.DateTimeFormat.AbbreviatedDayNames[(day + 1) % 7])),
    };

    private static string Status(HabitItem habit, double? ring, double value, int met, bool skipped, bool paused, IStrings strings) => (habit, ring) switch
    {
        _ when paused => strings.Get("Habits.Paused"),
        _ when skipped => strings.Get("Habits.Skipped"),
        ({ Cadence: HabitRules.PerWeek }, _) => strings.Get("Habits.MetWeek", met, habit.Times ?? 1),
        ({ Cadence: HabitRules.PerMonth }, _) => strings.Get("Habits.MetMonth", met, habit.Times ?? 1),
        (_, null) => strings.Get("Habits.NotDue"),
        ({ Measure: HabitRules.Check }, _) => strings.Get(ring >= 1 ? "Habits.Done" : "Habits.NotYet"),
        ({ Unit: { } unit }, _) => strings.Get("Habits.ValueUnit", Amount(value), Amount(habit.Target ?? 0), unit),
        _ => strings.Get("Habits.Value", Amount(value), Amount(habit.Target ?? 0)),
    };
}
