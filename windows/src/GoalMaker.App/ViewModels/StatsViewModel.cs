using System.Collections.ObjectModel;
using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The Stats page (docs/stats.md, spec stories 64 and 67): tasks finished week by week, goals hit
/// month by month, how the habits are holding up, the mood and energy of past reviews, what became
/// of the wants, and where the time went over twelve weeks (Tally, docs/tally.md).
/// </summary>
public sealed partial class StatsViewModel : ObservableObject
{
    private readonly TaskList tasks;
    private readonly GoalList goals;
    private readonly HabitList habits;
    private readonly ReviewList reviews;
    private readonly WantList? wants;
    private readonly TallyList? tally;
    private readonly Func<IReadOnlyList<TallyCategory>, TallyLabels>? tallyLabels;
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly TimeProvider time;

    [ObservableProperty]
    private bool isEmpty;

    [ObservableProperty]
    private string doneValue = "0";

    [ObservableProperty]
    private string donePerWeek = string.Empty;

    [ObservableProperty]
    private string goalsValue = string.Empty;

    [ObservableProperty]
    private string goalsHint = string.Empty;

    [ObservableProperty]
    private string habitsValue = string.Empty;

    [ObservableProperty]
    private string habitsHint = string.Empty;

    [ObservableProperty]
    private string firstWeek = string.Empty;

    [ObservableProperty]
    private bool hasMonths;

    [ObservableProperty]
    private bool hasHabits;

    [ObservableProperty]
    private bool hasRatings;

    [ObservableProperty]
    private IReadOnlyList<StatsDigest.Rating> ratings = [];

    [ObservableProperty]
    private bool hasWants;

    [ObservableProperty]
    private string wantsBought = "0";

    [ObservableProperty]
    private string wantsDropped = "0";

    [ObservableProperty]
    private string wantsNotSpent = "0";

    [ObservableProperty]
    private string wantsCurrency = string.Empty;

    [ObservableProperty]
    private bool hasTally;

    [ObservableProperty]
    private string tallyTotal = string.Empty;

    [ObservableProperty]
    private string tallyPerWeek = string.Empty;

    [ObservableProperty]
    private string tallyFirstWeek = string.Empty;

    public StatsViewModel(
        TaskList tasks,
        GoalList goals,
        HabitList habits,
        ReviewList reviews,
        ISettingsStore settings,
        IStrings strings,
        TimeProvider time,
        Action<Action> runOnUi,
        WantList? wants = null,
        TallyList? tally = null,
        Func<IReadOnlyList<TallyCategory>, TallyLabels>? tallyLabels = null)
    {
        this.wants = wants;
        this.tally = tally;
        this.tallyLabels = tallyLabels;
        this.tasks = tasks;
        this.goals = goals;
        this.habits = habits;
        this.reviews = reviews;
        this.settings = settings;
        this.strings = strings;
        this.time = time;
        tasks.Changed += (_, _) => runOnUi(Refresh);
        goals.Changed += (_, _) => runOnUi(Refresh);
        habits.Changed += (_, _) => runOnUi(Refresh);
        reviews.Changed += (_, _) => runOnUi(Refresh);
        if (wants is not null)
        {
            wants.Changed += (_, _) => runOnUi(Refresh);
        }

        if (tally is not null)
        {
            tally.Changed += (_, _) => runOnUi(Refresh);
        }

        Refresh();
    }

    /// <summary>A bar per week, the week holding today last.</summary>
    public ObservableCollection<StatsBarViewModel> Weeks { get; } = [];

    /// <summary>A bar per month, as full as the share of its goals that were hit.</summary>
    public ObservableCollection<StatsBarViewModel> Months { get; } = [];

    /// <summary>A row per habit that is not archived.</summary>
    public ObservableCollection<StatsHabitViewModel> HabitRows { get; } = [];

    /// <summary>Tally's twelve weeks, the one holding today last, each stacked by category.</summary>
    public ObservableCollection<TallyBarViewModel> TallyWeeks { get; } = [];

    /// <summary>The categories over the twelve weeks, most first.</summary>
    public ObservableCollection<TallySegmentViewModel> TallyLegend { get; } = [];

    public void Refresh()
    {
        var digest = StatsRules.Build(
            tasks.All(),
            goals.All(),
            goals.Entries(),
            habits.All(),
            habits.Checkins(),
            habits.Pauses(),
            reviews.All(),
            Today());

        var most = Math.Max(1, digest.Weeks.Count == 0 ? 1 : digest.Weeks.Max(week => week.Done));
        Weeks.Clear();
        foreach (var week in digest.Weeks)
        {
            Weeks.Add(new StatsBarViewModel(
                week.Start.ToString("d MMM", CultureInfo.CurrentCulture),
                week.Done > 0 ? week.Done.ToString(CultureInfo.CurrentCulture) : string.Empty,
                (double)week.Done / most,
                week.Done > 0));
        }

        Months.Clear();
        foreach (var month in digest.Months)
        {
            Months.Add(new StatsBarViewModel(
                month.Start.ToString("MMM", CultureInfo.CurrentCulture),
                month.Total > 0 ? strings.Get("Stats.MonthHit", month.Hit, month.Total) : string.Empty,
                month.Fraction,
                month.Total > 0));
        }

        HabitRows.Clear();
        foreach (var habit in digest.Habits)
        {
            HabitRows.Add(new StatsHabitViewModel(
                habit.Emoji ?? string.Empty,
                habit.Name,
                strings.Get("Stats.HabitMet", habit.Met, habit.Periods, habit.Best),
                Percent(habit.Rate),
                habit.Rate,
                habit.Streak.ToString(CultureInfo.CurrentCulture)));
        }

        DoneValue = digest.Done.ToString(CultureInfo.CurrentCulture);
        DonePerWeek = strings.Get("Stats.PerWeek", digest.PerWeek.ToString("0.0", CultureInfo.CurrentCulture));
        GoalsValue = strings.Get("Stats.GoalsValue", digest.GoalsHit, digest.GoalsTotal);
        GoalsHint = strings.Get("Stats.Months", digest.Months.Count);
        HabitsValue = Percent(digest.HabitRate);
        HabitsHint = strings.Get("Stats.HabitCount", digest.Habits.Count);
        FirstWeek = digest.Weeks.Count > 0 ? digest.Weeks[0].Start.ToString("d MMM", CultureInfo.CurrentCulture) : string.Empty;
        Ratings = digest.Ratings;
        HasMonths = digest.Months.Count > 0;
        HasHabits = digest.Habits.Count > 0;
        HasRatings = digest.Ratings.Count > 0;
        IsEmpty = digest.Empty;

        // What became of the wants (docs/wants.md), once one has been decided.
        if (wants is not null)
        {
            var currency = wants.Cooldowns().Currency;
            var decided = WantRules.Stats(wants.All(), currency);
            WantsBought = decided.Bought.ToString(CultureInfo.CurrentCulture);
            WantsDropped = decided.Dropped.ToString(CultureInfo.CurrentCulture);
            WantsNotSpent = decided.NotSpent.ToString("N0", CultureInfo.CurrentCulture);
            WantsCurrency = currency;
            HasWants = decided.Bought + decided.Dropped > 0;
        }

        ShowTally();
    }

    // Where the time went over twelve weeks, every device together (docs/tally.md), once there is any.
    private void ShowTally()
    {
        if (tally is null || tallyLabels is null)
        {
            return;
        }

        var today = Today();
        var first = GoalRules.PeriodStart(GoalHorizon.Week, today).AddDays(-7 * (TallyRules.WeekCount - 1));
        var rows = tally.Days(first, today);
        var labels = tallyLabels(tally.Categories());
        var weeks = TallyRules.Weeks(rows, today);
        var most = Math.Max(1, weeks.Max(week => week.Minutes));
        TallyWeeks.Clear();
        foreach (var week in weeks)
        {
            TallyWeeks.Add(new TallyBarViewModel(
                week.Start.ToString("d MMM", CultureInfo.CurrentCulture),
                week.Minutes > 0 ? labels.Duration(week.Minutes) : string.Empty,
                (double)week.Minutes / most,
                [.. week.Categories.Select(group => ((double)group.Minutes, labels.Brush(group.Key!)))],
                strings.Get("Tally.BarTip", week.Start.ToString("d MMM", CultureInfo.CurrentCulture), labels.Duration(week.Minutes)),
                week == weeks[^1]));
        }

        TallyLegend.Clear();
        foreach (var group in TallyRules.ByCategory(rows))
        {
            TallyLegend.Add(new TallySegmentViewModel(labels.Name(group.Key!), labels.Emoji(group.Key!), labels.Duration(group.Minutes), labels.Brush(group.Key!)));
        }

        var minutes = weeks.Sum(week => week.Minutes);
        TallyTotal = labels.Duration(minutes);
        TallyPerWeek = strings.Get("Stats.TallyPerWeek", labels.Duration(minutes / TallyRules.WeekCount));
        TallyFirstWeek = weeks[0].Start.ToString("d MMM", CultureInfo.CurrentCulture);
        HasTally = minutes > 0;
    }

    private static string Percent(double fraction) =>
        Math.Round(Math.Clamp(fraction, 0, 1) * 100).ToString("0", CultureInfo.CurrentCulture) + "%";

    private DateOnly Today() => PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);
}
