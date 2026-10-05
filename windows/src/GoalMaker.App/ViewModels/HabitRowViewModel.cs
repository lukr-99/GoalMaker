using System.Globalization;
using System.Windows;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One habit on the Habits page or Today (docs/habits.md; the habits prototype, option B): where today
/// stands, how often it runs, the streak, pips or a bar, the week's dots, the check-in button, its
/// heatmap (the page only, <see cref="IsFull"/>) and its actions. A row for a day other than today (the
/// calendar's, docs/calendar.md) checks in, skips and fails on that day.
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
        HabitsViewModel? owner = null,
        HabitStanding standing = HabitStanding.None,
        IReadOnlyList<HabitDotViewModel>? dots = null,
        bool full = false,
        DateOnly? day = null,
        double? used = null)
    {
        this.owner = owner;
        Habit = habit;
        Standing = standing;
        Dots = dots ?? [];
        IsFull = full;
        Ring = ring;
        Streak = streak;
        Value = value;
        Used = used ?? value;
        IsSkipped = skipped;
        IsPaused = paused;
        Heat = heat;
        Serves = goalTitle is null ? string.Empty : strings.Get("Habits.Serves", goalTitle);
        CadenceText = Cadence(habit, strings);
        StatusText = Status(habit, ring, value, Used, met, skipped, standing == HabitStanding.Failed, paused, day is not null, strings);
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
        FailText = strings.Get(habit.Cadence switch
        {
            HabitRules.PerWeek => "Habits.FailWeek",
            HabitRules.PerMonth => "Habits.FailMonth",
            _ => "Habits.FailDay",
        });
        CheckInText = strings.Get("Habits.CheckIn", habit.Name);
        Met = met;
        LineText = strings.Get("Habits.Line", CadenceText, habit.Archived ? strings.Get("Habits.ArchivedLine") : StatusText);
        MoreText = strings.Get("Habits.More", habit.Name);
        DotsText = strings.Get(
            "Habits.Dots",
            Dots.Count(dot => dot.IsMet),
            Dots.Count(dot => dot.IsMissed || dot.IsOver),
            Dots.Count(dot => dot.IsSkipped));
        Pips = PipsFor(habit, Used, met);
        HasBar = Pips.Count == 0 && (habit.Measure != HabitRules.Check || habit.Cadence is HabitRules.PerWeek or HabitRules.PerMonth);
        var share = Math.Clamp(ring ?? 0, 0, 1);
        BarFilled = new GridLength(share, GridUnitType.Star);
        BarRest = new GridLength(1 - share, GridUnitType.Star);
        var takesBack = habit.Measure == HabitRules.Check && value >= 1;
        ButtonText = skipped ? strings.Get("Habits.UnskipOn", habit.Name) : IsFailed ? strings.Get("Habits.UnfailOn", habit.Name) : habit.Measure switch
        {
            HabitRules.Check => strings.Get(takesBack ? "Habits.TakeBack" : "Habits.CheckIn", habit.Name),
            HabitRules.Count => strings.Get("Habits.AddOne", habit.Name),
            _ => strings.Get("Habits.LogOn", habit.Name),
        };
        CheckInMenuText = strings.Get(habit.Measure == HabitRules.Count ? "Habits.MenuAddOne" : takesBack ? "Habits.MenuTakeBack" : "Habits.MenuCheckIn");
        LogOneText = strings.Get("Habits.LogOne", habit.Name);
        LogExactText = strings.Get("Habits.LogExact", habit.Name);
        AmountHint = habit.Unit ?? strings.Get("Habits.Amount");
        RingText = habit.Emoji ?? string.Empty;
        CheckInCommand = new RelayCommand(() => owner?.Tap(habit, day));
        OpenHabitsCommand = new RelayCommand(() => owner?.OpenPage());
        LogOneCommand = new RelayCommand(() => owner?.LogOne(habit, day));
        LogAmountCommand = new RelayCommand(() => owner?.LogTyped(habit, AmountText, day));
        EditCommand = new RelayCommand(() => owner?.Edit(habit));
        LogCommand = new RelayCommand(() => owner?.StartLog(habit, day));
        ClearCommand = new RelayCommand(() => owner?.ClearToday(habit.Id, day));
        SkipCommand = new RelayCommand(() => owner?.Skip(habit.Id, true, day));
        UnskipCommand = new RelayCommand(() => owner?.Skip(habit.Id, false, day));
        FailCommand = new RelayCommand(() => owner?.Fail(habit.Id, true, day));
        UnfailCommand = new RelayCommand(() => owner?.Fail(habit.Id, false, day));
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

    /// <summary>
    /// What a limit has had in its period by the row's day: the day's value, or the week's or month's so
    /// far (contracts/vectors/habits.json, over). For a habit to build it is the day's value.
    /// </summary>
    public double Used { get; }

    public IReadOnlyList<HabitHeat> Heat { get; }

    public bool IsSkipped { get; }

    public bool IsPaused { get; }

    /// <summary>The owner said today's period failed: missed now, neither done nor left.</summary>
    public bool IsFailed => Standing == HabitStanding.Failed;

    public bool IsArchived => Habit.Archived;

    /// <summary>The habit's number is a limit, so the ring fills with what has been had (docs/habits.md).</summary>
    public bool IsLimit => HabitRules.IsLimit(Habit);

    /// <summary>The period went over the limit by today: the ring, the number and the day turn to the danger colour.</summary>
    public bool IsOver => !IsSkipped && !IsFailed && HabitRules.IsOver(Habit, Used);

    /// <summary>Where the habit stands today (contracts/vectors/habits.json, standings).</summary>
    public HabitStanding Standing { get; }

    /// <summary>Today's part is done: Hide done hides it. A limit and a skip are never done.</summary>
    public bool IsDone => Standing == HabitStanding.Done;

    /// <summary>Still to do today: Today's count of what is left counts it.</summary>
    public bool IsLeft => Standing == HabitStanding.Left;

    /// <summary>The Habits page's group: every day, weekly or limits.</summary>
    public HabitGroup Group => HabitRules.Group(Habit);

    /// <summary>A card on the Habits page, with how often, the goal, the Not on Today mark and the heatmap.</summary>
    public bool IsFull { get; }

    /// <summary>Days met so far in the period holding today.</summary>
    public int Met { get; }

    /// <summary>The seven days up to today, oldest first.</summary>
    public IReadOnlyList<HabitDotViewModel> Dots { get; }

    /// <summary>The week's dots in words, for a screen reader.</summary>
    public string DotsText { get; }

    /// <summary>
    /// A pip for each glass of a small count, each day a weekly habit needs, or each day a weekly limit
    /// allows; none for the rest.
    /// </summary>
    public IReadOnlyList<HabitPipViewModel> Pips { get; }

    public bool HasPips => Pips.Count > 0;

    /// <summary>An amount or a big count fills a bar instead of pips; a check habit has neither.</summary>
    public bool HasBar { get; }

    public GridLength BarFilled { get; }

    public GridLength BarRest { get; }

    /// <summary>Pips or a bar show while the habit takes check-ins and is neither skipped nor failed.</summary>
    public bool ShowsPips => HasPips && CanCheckIn && !IsSkipped && !IsFailed;

    public bool ShowsBar => HasBar && CanCheckIn && !IsSkipped && !IsFailed;

    /// <summary>"Every day · 5 of 8 glasses today", or "Every day · Archived".</summary>
    public string LineText { get; }

    /// <summary>The card's line under the name: the full line on the page, where today stands on Today.</summary>
    public string CardLine => IsFull || IsArchived ? LineText : StatusText;

    /// <summary>What the check-in button does, for a reader and a tooltip.</summary>
    public string ButtonText { get; }

    /// <summary>The check-in button fills with the accent and turns round once today's part is done.</summary>
    public bool IsOn => IsDone;

    /// <summary>A count adds one, so its button says +1.</summary>
    public bool ShowsPlusOne => !IsSkipped && !IsFailed && Habit.Measure == HabitRules.Count;

    public bool ShowsCheckGlyph => !IsSkipped && !IsFailed && !ShowsPlusOne && (IsOn || (IsLimit && Habit.Measure == HabitRules.Check && Value >= 1));

    public bool ShowsAddGlyph => !IsSkipped && !IsFailed && !ShowsPlusOne && !ShowsCheckGlyph;

    /// <summary>The button undoes a skip or a fail, or checks in.</summary>
    public IRelayCommand ButtonCommand => IsSkipped ? UnskipCommand : IsFailed ? UnfailCommand : CheckInCommand;

    /// <summary>The menu's first choice: check in, take today's check-in back, or add one.</summary>
    public string CheckInMenuText { get; }

    /// <summary>A check or a count checks in from the menu; an amount logs instead.</summary>
    public bool CanCheckInFromMenu => CanCheckIn && !IsSkipped && Habit.Measure != HabitRules.Amount;

    public bool CanLogFromMenu => CanCheckIn && !IsSkipped && Habit.Measure != HabitRules.Check;

    public bool CanUnskip => IsSkipped && !IsArchived;

    /// <summary>A habit that takes check-ins today can fail, unless it is skipped or failed already.</summary>
    public bool CanFail => CanCheckIn && !IsSkipped && !IsFailed;

    public bool CanUnfail => IsFailed && !IsArchived;

    /// <summary>An archived habit takes no check-ins, so its card has no button.</summary>
    public bool ShowsButton => !IsArchived;

    /// <summary>A card on Today offers the way to the Habits page; the page's own cards don't.</summary>
    public bool ShowsOpenHabits => !IsFull;

    /// <summary>The goal it serves, on the page's cards.</summary>
    public bool ShowsServes => IsFull && HasServes;

    /// <summary>The Not on Today mark, on the page's cards.</summary>
    public bool ShowsOffToday => IsFull && IsOffToday;

    public string MoreText { get; }

    public string StreakNumber => Streak.ToString(CultureInfo.CurrentCulture);

    public bool IsStreakLit => Streak > 0;

    /// <summary>The emoji on the card's tile, or the name's first letter when the habit has none.</summary>
    public string TileText => Habit.Emoji ?? (Habit.Name.Length > 0 ? Habit.Name[..1].ToUpper(CultureInfo.CurrentCulture) : string.Empty);

    public bool HasEmoji => Habit.Emoji is not null;

    /// <summary>The ring shows a check: done without an emoji.</summary>
    public bool ShowsCheck => IsDone && Habit.Emoji is null;

    /// <summary>The habit takes check-ins today: it isn't archived, paused, or off duty.</summary>
    public bool CanCheckIn => !IsArchived && !IsPaused && Ring is not null && Standing is not (HabitStanding.None or HabitStanding.Paused);

    public bool CanLog => CanCheckIn && Habit.Measure != HabitRules.Check;

    public bool CanClear => Value > 0;

    public bool CanSkip => CanCheckIn && !IsSkipped;

    public bool CanPause => !IsArchived && !IsPaused;

    public string RingText { get; }

    public string CadenceText { get; }

    public string StatusText { get; }

    public string StreakText { get; }

    public bool HasStreak => StreakText.Length > 0;

    public string SkipText { get; }

    /// <summary>Fail today, this week or this month.</summary>
    public string FailText { get; }

    public string CheckInText { get; }

    /// <summary>What a press of the plus says it does, for a reader and a tooltip.</summary>
    public string LogOneText { get; }

    public string LogExactText { get; }

    /// <summary>The unit, or the word for a number when the habit has none.</summary>
    public string AmountHint { get; }

    /// <summary>
    /// The number typed beside the row (docs/habits.md). The rows are made again after every
    /// check-in, so what was typed goes with the row it was typed in.
    /// </summary>
    public string AmountText { get; set; } = string.Empty;

    public string Serves { get; }

    public bool HasServes => Serves.Length > 0;

    /// <summary>The habit is kept off Today: the Habits page says so under its name.</summary>
    public bool IsOffToday => !Habit.ShowOnToday && !Habit.Archived;

    public IRelayCommand CheckInCommand { get; }

    /// <summary>The Habits page, from a card on Today.</summary>
    public IRelayCommand OpenHabitsCommand { get; }

    public IRelayCommand EditCommand { get; }

    public IRelayCommand LogCommand { get; }

    /// <summary>One more of whatever this counts, without opening anything.</summary>
    public IRelayCommand LogOneCommand { get; }

    /// <summary>Exactly the number beside the row.</summary>
    public IRelayCommand LogAmountCommand { get; }

    public IRelayCommand ClearCommand { get; }

    public IRelayCommand SkipCommand { get; }

    public IRelayCommand UnskipCommand { get; }

    public IRelayCommand FailCommand { get; }

    public IRelayCommand UnfailCommand { get; }

    public IRelayCommand PauseCommand { get; }

    public IRelayCommand ResumeCommand { get; }

    public IRelayCommand ArchiveCommand { get; }

    public IRelayCommand UnarchiveCommand { get; }

    public IRelayCommand DeleteCommand { get; }

    /// <summary>
    /// How often a habit runs: "Every day", "Mon, Wed, Fri", "3 times a week". A weekly or monthly limit
    /// reads "At most 2 days a week" or "Not once a month" for a check, and "Every week" for a count
    /// or an amount, whose number is the most for the whole week.
    /// </summary>
    public static string Cadence(HabitItem habit, IStrings strings)
    {
        if (habit.Cadence is not (HabitRules.PerWeek or HabitRules.PerMonth))
        {
            return habit.Cadence == HabitRules.OnWeekdays ? Weekdays(habit.Weekdays ?? 0, strings) : strings.Get("Habits.CadenceDaily");
        }

        var period = habit.Cadence == HabitRules.PerMonth ? "Month" : "Week";
        var times = habit.Times ?? 1;
        if (!HabitRules.IsLimit(habit))
        {
            return strings.Get("Habits.Times" + period, times);
        }

        if (habit.Measure != HabitRules.Check)
        {
            return strings.Get("Habits.Every" + period);
        }

        return times == 0 ? strings.Get("Habits.NotOnce" + period) : strings.Get("Habits.AtMost" + period, times);
    }

    // More pips than this read as a bar.
    private const int MaxPips = 12;

    // The used value is the day's value, or what a limit has had in its week or month so far.
    private static List<HabitPipViewModel> PipsFor(HabitItem habit, double used, int met)
    {
        // A limit's pips fill with what was had; the ones past its number turn to the danger colour.
        if (HabitRules.IsLimit(habit))
        {
            if (habit.Measure == HabitRules.Amount || (habit.Measure == HabitRules.Check && !HabitRules.IsPeriodic(habit)))
            {
                return [];
            }

            var most = (int)Math.Ceiling(HabitRules.Limit(habit));
            var had = Math.Max(most, (int)Math.Ceiling(used));
            return had > MaxPips ? [] : [.. Enumerable.Range(0, had).Select(index => new HabitPipViewModel(index < used, index >= most && index < used))];
        }

        if (HabitRules.IsPeriodic(habit))
        {
            var times = habit.Times ?? 1;
            return times > MaxPips ? [] : [.. Enumerable.Range(0, times).Select(index => new HabitPipViewModel(index < met, false))];
        }

        if (habit.Measure != HabitRules.Count)
        {
            return [];
        }

        var target = (int)Math.Ceiling(habit.Target ?? 1);
        var count = Math.Max(target, (int)Math.Ceiling(used));
        return count > MaxPips ? [] : [.. Enumerable.Range(0, count).Select(index => new HabitPipViewModel(index < used, false))];
    }

    // "1 of at most 2 this week", "3 of at most 5 drinks this month", or for a limit of 0 "None this week".
    private static string PeriodLimit(HabitItem habit, double used, IStrings strings)
    {
        var period = habit.Cadence == HabitRules.PerMonth ? "Month" : "Week";
        var most = HabitRules.Limit(habit);
        if (most <= 0)
        {
            return strings.Get((used > 0 ? "Habits.Over" : "Habits.None") + period);
        }

        return habit.Measure != HabitRules.Check && habit.Unit is { } unit
            ? strings.Get("Habits.LimitUnit" + period, Amount(used), Amount(most), unit)
            : strings.Get("Habits.Limit" + period, Amount(used), Amount(most));
    }

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

    private static string Status(HabitItem habit, double? ring, double value, double used, int met, bool skipped, bool failed, bool paused, bool onDay, IStrings strings)
    {
        // A day other than today (the calendar's) says what happened without "today".
        string Key(string key) => onDay ? key + "OnDay" : key;
        return (habit, ring) switch
        {
            _ when paused => strings.Get("Habits.Paused"),
            _ when skipped => strings.Get("Habits.Skipped"),
            _ when failed => strings.Get("Habits.Failed"),
            ({ Direction: HabitRules.AtMost, Cadence: HabitRules.PerWeek or HabitRules.PerMonth }, _) => PeriodLimit(habit, used, strings),
            ({ Cadence: HabitRules.PerWeek }, _) => strings.Get("Habits.MetWeek", met, habit.Times ?? 1),
            ({ Cadence: HabitRules.PerMonth }, _) => strings.Get("Habits.MetMonth", met, habit.Times ?? 1),
            (_, null) => strings.Get("Habits.NotDue"),
            ({ Direction: HabitRules.AtMost, Measure: HabitRules.Check }, _) =>
                strings.Get(Key(value >= 1 ? "Habits.OverToday" : "Habits.NoneToday")),
            ({ Direction: HabitRules.AtMost, Unit: { } unit }, _) =>
                strings.Get(Key("Habits.LimitUnit"), Amount(value), Amount(habit.Target ?? 0), unit),
            ({ Direction: HabitRules.AtMost }, _) => strings.Get(Key("Habits.Limit"), Amount(value), Amount(habit.Target ?? 0)),
            ({ Measure: HabitRules.Check }, _) => strings.Get(Key(ring >= 1 ? "Habits.Done" : "Habits.NotYet")),
            ({ Unit: { } unit }, _) => strings.Get(Key("Habits.ValueUnit"), Amount(value), Amount(habit.Target ?? 0), unit),
            _ => strings.Get(Key("Habits.Value"), Amount(value), Amount(habit.Target ?? 0)),
        };
    }
}
