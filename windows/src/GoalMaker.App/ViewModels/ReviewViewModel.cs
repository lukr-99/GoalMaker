using System.Collections.ObjectModel;
using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The guided review of one period (docs/reviews.md, spec stories 59 to 64): look back, handle what is
/// still open, reflect on the prompts the library and the period's facts give, rate mood and energy,
/// and set the next period's goals. Everything is saved as it is answered.
/// </summary>
public sealed partial class ReviewViewModel : ObservableObject
{
    // How many prompts a review asks, and how many of them may come from the period's facts.
    private const int Questions = 3;
    private const int MaxReactive = 2;

    private readonly ReviewList reviews;
    private readonly TaskList tasks;
    private readonly AreaList areas;
    private readonly GoalList goals;
    private readonly HabitList habits;
    private readonly PromptLibrary prompts;
    private readonly RitualRunList rituals;
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly TimeProvider time;
    private ReviewItem? review;
    private bool loading;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsLookBack), nameof(IsTasks), nameof(IsReflect), nameof(IsRate), nameof(IsGoals), nameof(IsDone), nameof(NextLabel), nameof(Progress), nameof(CanGoBack))]
    private ReviewStep step = ReviewStep.LookBack;

    [ObservableProperty]
    private ReviewDigest digest = new();

    [ObservableProperty]
    private string heading = string.Empty;

    [ObservableProperty]
    private string periodText = string.Empty;

    [ObservableProperty]
    private string doneText = string.Empty;

    [ObservableProperty]
    private string changeText = string.Empty;

    [ObservableProperty]
    private IReadOnlyList<string> highlights = [];

    [ObservableProperty]
    private int? mood;

    [ObservableProperty]
    private int? energy;

    [ObservableProperty]
    private string summaryText = string.Empty;

    public ReviewViewModel(
        string kind,
        DateOnly periodStart,
        ReviewList reviews,
        TaskList tasks,
        AreaList areas,
        GoalList goals,
        HabitList habits,
        PromptLibrary prompts,
        RitualRunList rituals,
        ISettingsStore settings,
        IStrings strings,
        TimeProvider time,
        Action<Action> runOnUi)
    {
        Kind = kind;
        PeriodStart = periodStart;
        this.reviews = reviews;
        this.tasks = tasks;
        this.areas = areas;
        this.goals = goals;
        this.habits = habits;
        this.prompts = prompts;
        this.rituals = rituals;
        this.settings = settings;
        this.strings = strings;
        this.time = time;
        tasks.Changed += (_, _) => runOnUi(Refresh);
        goals.Changed += (_, _) => runOnUi(Refresh);
        habits.Changed += (_, _) => runOnUi(Refresh);
        Start();
    }

    public string Kind { get; private set; }

    public DateOnly PeriodStart { get; private set; }

    public ObservableCollection<ReviewQuestionViewModel> QuestionRows { get; } = [];

    public ObservableCollection<ReviewTaskViewModel> OpenTasks { get; } = [];

    public ObservableCollection<GoalRowViewModel> Goals { get; } = [];

    public ObservableCollection<ReviewDigest.Day> Days { get; } = [];

    public ObservableCollection<ReviewDigest.Goal> PeriodGoals { get; } = [];

    public ObservableCollection<ReviewDigest.Habit> PeriodHabits { get; } = [];

    public bool IsLookBack => Step == ReviewStep.LookBack;

    public bool IsTasks => Step == ReviewStep.Tasks;

    public bool IsReflect => Step == ReviewStep.Reflect;

    public bool IsRate => Step == ReviewStep.Rate;

    public bool IsGoals => Step == ReviewStep.Goals;

    public bool IsDone => Step == ReviewStep.Done;

    /// <summary>How far the review has come, 0 to 1, for the bar across the top.</summary>
    public double Progress => ((int)Step + 1) / (double)Enum.GetValues<ReviewStep>().Length;

    public string NextLabel => strings.Get(Step switch
    {
        ReviewStep.Goals => "Reviews.Finish",
        ReviewStep.Done => "Reviews.Close",
        _ => "Reviews.Next",
    });

    /// <summary>Opens (or picks up) the review of a period; the page reuses one view model.</summary>
    public void Open(string kind, DateOnly periodStart)
    {
        Kind = kind;
        PeriodStart = periodStart;
        Step = ReviewStep.LookBack;
        Start();
    }

    public void Refresh()
    {
        var today = Today();
        Digest = ReviewLookBack.Build(
            Kind,
            PeriodStart,
            tasks.All(),
            areas.All(),
            goals.All(),
            goals.Entries(),
            habits.All(),
            habits.Checkins(),
            habits.Pauses(),
            today);
        Heading = strings.Get(Kind == ReviewRules.Monthly ? "Reviews.Monthly" : "Reviews.Weekly");
        PeriodText = strings.Get(
            "Goals.Range",
            Digest.PeriodStart.ToString("d MMM", CultureInfo.CurrentCulture),
            Digest.PeriodEnd.ToString("d MMM", CultureInfo.CurrentCulture));
        DoneText = strings.Get("Reviews.Done", Digest.Done);
        ChangeText = Digest.DoneBefore == 0
            ? strings.Get("Reviews.NoLastPeriod")
            : Digest.Change > 0
                ? strings.Get("Reviews.MoreThanBefore", Digest.Change)
                : Digest.Change < 0
                    ? strings.Get("Reviews.FewerThanBefore", -Digest.Change)
                    : strings.Get("Reviews.SameAsBefore");
        var highlights = new List<string>();
        if (Digest.BestDay is { } best)
        {
            highlights.Add(strings.Get("Reviews.BestDay", best.Date.ToString("dddd", CultureInfo.CurrentCulture), best.Done));
        }

        if (Digest.StrongestArea is { } area)
        {
            highlights.Add(strings.Get("Reviews.StrongestArea", area.Name, area.Done));
        }

        if (Digest.Habits.OrderByDescending(habit => habit.Streak).FirstOrDefault() is { Streak: > 0 } streak)
        {
            highlights.Add(strings.Get("Reviews.LongestStreak", streak.Name, streak.Streak));
        }

        Highlights = highlights;
        Days.Clear();
        foreach (var day in Digest.Days)
        {
            Days.Add(day);
        }

        PeriodGoals.Clear();
        foreach (var goal in Digest.Goals)
        {
            PeriodGoals.Add(goal);
        }

        PeriodHabits.Clear();
        foreach (var habit in Digest.Habits)
        {
            PeriodHabits.Add(habit);
        }

        OpenTasks.Clear();
        foreach (var task in Digest.OpenTasks)
        {
            OpenTasks.Add(new ReviewTaskViewModel(task, Decide));
        }

        ShowNextGoals();
        OnPropertyChanged(nameof(HasOpenTasks));
        OnPropertyChanged(nameof(HasPeriodGoals));
        OnPropertyChanged(nameof(HasPeriodHabits));
        OnPropertyChanged(nameof(MostDone));
        OnPropertyChanged(nameof(OpenTasksText));
        OnPropertyChanged(nameof(DoneSummary));
    }

    public bool HasOpenTasks => OpenTasks.Count > 0;

    public bool HasPeriodGoals => PeriodGoals.Count > 0;

    public bool HasPeriodHabits => PeriodHabits.Count > 0;

    /// <summary>The busiest day of the period, which fills a day's bar.</summary>
    public int MostDone => Days.Count == 0 ? 1 : Math.Max(1, Days.Max(day => day.Done));

    /// <summary>Back leaves the first step to the page, not to the review.</summary>
    public bool CanGoBack => Step != ReviewStep.LookBack;

    public string OpenTasksText => strings.Get(HasOpenTasks ? "Reviews.OpenIntro" : "Reviews.OpenNone");

    public string DoneSummary => strings.Get("Reviews.DoneSummary", Digest.Done, QuestionRows.Count(question => !string.IsNullOrWhiteSpace(question.Answer)));

    public bool CanCopyGoals { get; private set; }

    /// <summary>Keeps what was written; the page calls it when it leaves, so nothing is lost.</summary>
    public void SaveAnswers()
    {
        if (review is null || QuestionRows.Count == 0 || loading)
        {
            return;
        }

        reviews.SetReflections(review.Id, QuestionRows.Select(question => new Reflection(question.PromptId, question.Answer)));
    }

    [RelayCommand]
    private void Next()
    {
        SaveAnswers();
        if (Step == ReviewStep.Goals)
        {
            rituals.Record(Kind == ReviewRules.Monthly ? RitualRunList.MonthlyReview : RitualRunList.WeeklyReview, Today());
            Step = ReviewStep.Done;
            OnPropertyChanged(nameof(DoneSummary));
            return;
        }

        if (Step != ReviewStep.Done)
        {
            Step = (ReviewStep)((int)Step + 1);
        }
    }

    [RelayCommand]
    private void Back()
    {
        if (Step == ReviewStep.LookBack)
        {
            return;
        }

        SaveAnswers();
        Step = (ReviewStep)((int)Step - 1);
    }

    [RelayCommand]
    private void SetMood(int value)
    {
        if (review is null)
        {
            return;
        }

        Mood = Mood == value ? null : value;
        reviews.SetMood(review.Id, Mood);
    }

    [RelayCommand]
    private void SetEnergy(int value)
    {
        if (review is null)
        {
            return;
        }

        Energy = Energy == value ? null : value;
        reviews.SetEnergy(review.Id, Energy);
    }

    [RelayCommand]
    private void CopyGoals()
    {
        goals.CopyPrevious(ReviewLookBack.HorizonOf(Kind), ReviewLookBack.PeriodEnd(Kind, PeriodStart).AddDays(1));
        ShowNextGoals();
    }

    private void Start()
    {
        loading = true;
        try
        {
            review = reviews.Open(Kind, PeriodStart);
            Mood = review?.Mood;
            Energy = review?.Energy;
            SummaryText = review?.Summary ?? string.Empty;
            Refresh();
            ShowQuestions();
        }
        finally
        {
            loading = false;
        }
    }

    // The prompts this review asks: the ones it asked before, or the period's own plus the rotation.
    private void ShowQuestions()
    {
        QuestionRows.Clear();
        var asked = review?.Reflections ?? [];
        if (asked.Count > 0)
        {
            foreach (var reflection in asked)
            {
                var prompt = prompts[reflection.PromptId];
                QuestionRows.Add(new ReviewQuestionViewModel(
                    reflection.PromptId,
                    prompt is null ? reflection.PromptId : PromptRules.Text(prompt, Kind),
                    reflection.Answer,
                    SaveAnswers));
            }

            return;
        }

        var reactive = PromptRules.Reactive(prompts, Kind, Digest.Facts).Take(MaxReactive).ToList();
        var shown = reviews.All()
            .Where(other => other.Kind == Kind)
            .OrderBy(other => other.PeriodStart)
            .SelectMany(other => other.Reflections)
            .Select(reflection => reflection.PromptId)
            .ToList();
        foreach (var question in reactive)
        {
            QuestionRows.Add(new ReviewQuestionViewModel(question.PromptId, question.Text, string.Empty, SaveAnswers));
        }

        foreach (var prompt in PromptRules.Rotation(prompts, Kind, shown, Questions - reactive.Count))
        {
            QuestionRows.Add(new ReviewQuestionViewModel(prompt.Id, PromptRules.Text(prompt, Kind), string.Empty, SaveAnswers));
        }
    }

    private void ShowNextGoals()
    {
        var horizon = ReviewLookBack.HorizonOf(Kind);
        var nextStart = ReviewLookBack.PeriodEnd(Kind, PeriodStart).AddDays(1);
        var all = goals.All();
        var own = all.Where(goal => goal.Horizon == horizon && goal.PeriodStart == nextStart && goal.Status != GoalRules.Dropped).ToList();
        var entries = goals.Entries().ToLookup(entry => entry.GoalId, StringComparer.Ordinal);
        var taskList = tasks.All().Where(task => task.GoalId is not null).ToLookup(task => task.GoalId!, StringComparer.Ordinal);
        Goals.Clear();
        foreach (var goal in own)
        {
            var progress = GoalRules.Progress(goal.Mode, goal.Status, goal.Target, taskList[goal.Id], entries[goal.Id]);
            Goals.Add(new GoalRowViewModel(goal, progress, null, 0, strings));
        }

        CanCopyGoals = own.Count == 0 && all.Any(goal => goal.Horizon == horizon && goal.PeriodStart == PeriodStart && goal.Status != GoalRules.Dropped);
        OnPropertyChanged(nameof(CanCopyGoals));
    }

    private void Decide(TaskItem task, string decision)
    {
        switch (decision)
        {
            case "forward":
                var next = ReviewLookBack.PeriodEnd(Kind, PeriodStart).AddDays(1);
                tasks.Plan(task.Id, next > Today() ? next : Today());
                break;
            case "done":
                tasks.SetDone(task.Id, true);
                break;
            default:
                tasks.Drop(task.Id);
                break;
        }
    }

    private DateOnly Today() => PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);
}
