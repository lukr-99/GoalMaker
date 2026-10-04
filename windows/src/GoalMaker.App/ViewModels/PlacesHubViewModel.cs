using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Navigation;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The Places page (ADR 0014), which All places opens: a live tile for every place, as on the phone's
/// Places hub, the pinned ones marked, and Edit, where a click pins or unpins a place. The PC has no
/// pin limit, so only the last pin refuses. The numbers come from the same rules and helpers the
/// places themselves use (Today's summary and habit rings, this week's goals, the archive, Stats' week,
/// Wants' states, the open life goals, Tally's day), so a tile never disagrees with its page.
/// </summary>
public sealed partial class PlacesHubViewModel : ObservableObject
{
    private const int ComingDays = 7;

    private readonly PlacesViewModel places;
    private readonly TaskList tasks;
    private readonly HabitsViewModel habitsPage;
    private readonly HabitList habits;
    private readonly GoalList goals;
    private readonly LifeGoalList lifeGoals;
    private readonly ReviewList reviews;
    private readonly WantList wants;
    private readonly TallyList tally;
    private readonly Func<IReadOnlyList<TallyCategory>, TallyLabels> tallyLabels;
    private readonly ISettingsStore settings;
    private readonly IStrings strings;
    private readonly TimeProvider time;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(EditLabel))]
    private bool isEditing;

    /// <summary>The line under the title: what is pinned, or while editing what a click does.</summary>
    [ObservableProperty]
    private string hint = string.Empty;

    public PlacesHubViewModel(
        PlacesViewModel places,
        TaskList tasks,
        HabitsViewModel habitsPage,
        HabitList habits,
        GoalList goals,
        LifeGoalList lifeGoals,
        ReviewList reviews,
        WantList wants,
        TallyList tally,
        Func<IReadOnlyList<TallyCategory>, TallyLabels> tallyLabels,
        ISettingsStore settings,
        IStrings strings,
        TimeProvider time,
        Action<Action> runOnUi)
    {
        this.places = places;
        this.tasks = tasks;
        this.habitsPage = habitsPage;
        this.habits = habits;
        this.goals = goals;
        this.lifeGoals = lifeGoals;
        this.reviews = reviews;
        this.wants = wants;
        this.tally = tally;
        this.tallyLabels = tallyLabels;
        this.settings = settings;
        this.strings = strings;
        this.time = time;
        var pinned = strings.Get("Places.IsPinned");
        var notPinned = strings.Get("Places.NotPinned");
        Tiles = [.. PlaceRules.Places.Select(place => new PlaceTileViewModel(place, places.Label(place), pinned, notPinned, Activate))];
        tasks.Changed += (_, _) => runOnUi(Refresh);
        habits.Changed += (_, _) => runOnUi(Refresh);
        goals.Changed += (_, _) => runOnUi(Refresh);
        lifeGoals.Changed += (_, _) => runOnUi(Refresh);
        reviews.Changed += (_, _) => runOnUi(Refresh);
        wants.Changed += (_, _) => runOnUi(Refresh);
        tally.Changed += (_, _) => runOnUi(Refresh);
        places.PinsChanged += (_, _) => ShowPins();
        Refresh();
    }

    /// <summary>Every place in the order All places lists them.</summary>
    public IReadOnlyList<PlaceTileViewModel> Tiles { get; }

    /// <summary>Edit or Done, on the button beside the hint.</summary>
    public string EditLabel => strings.Get(IsEditing ? "Places.Done" : "Places.Edit");

    /// <summary>Reads every place's numbers again; also called when the planning day moves on.</summary>
    public void Refresh()
    {
        var today = PlanningDay.Of(time.GetLocalNow().DateTime, settings.DayStartHour);
        var all = tasks.All();
        var lists = ListRules.Lists(all, today);
        var open = all.Where(task => task.State == TaskState.Open).ToList();
        var items = open.Where(task => task.ProjectId is not null).ToList();
        // The Habits tile counts every habit due today, the ones kept off Today too.
        var habitRows = habitsPage.DueRows();
        var goalRows = GoalsViewModel.ThisWeek(goals, tasks, today, strings, habits);
        var openLifeGoals = lifeGoals.All().Count(goal => goal.Status == LifeGoalRules.Open);
        var wantStates = wants.All().Select(want => WantRules.State(want, today)).ToList();
        var ready = wantStates.Count(state => state == WantState.Ready);
        var labels = tallyLabels(tally.Categories());
        var todays = TallyRules.ByCategory(tally.Days(today, today));
        var tallyMinutes = todays.Sum(group => group.Minutes);

        foreach (var tile in Tiles)
        {
            switch (tile.Id)
            {
                case PlaceRules.Today:
                    Ring(tile, lists.Summary.Done, lists.Summary.Total);
                    break;
                case PlaceRules.Tomorrow:
                    Line(tile, strings.Get("Places.TomorrowLine", lists.Tomorrow.Count));
                    break;
                case PlaceRules.Inbox:
                    Number(tile, lists.Inbox.Count, strings.Get("Places.InboxWord"));
                    break;
                case PlaceRules.Calendar:
                    Line(tile, strings.Get("Places.CalendarLine", open.Count(task =>
                        task.PlannedDate is { } day && day > today && day <= today.AddDays(ComingDays))));
                    break;
                case PlaceRules.Habits when habitRows.Count == 0:
                    Line(tile, strings.Get("Places.HabitsNone"));
                    break;
                case PlaceRules.Habits:
                    Ring(tile, habitRows.Count(row => row.IsDone), habitRows.Count);
                    break;
                case PlaceRules.Goals when goalRows.Count == 0:
                    Line(tile, strings.Get("Places.GoalsNone"));
                    break;
                case PlaceRules.Goals:
                    Ring(tile, goalRows.Count(row => row.IsHit), goalRows.Count);
                    break;
                case PlaceRules.LifeGoals when openLifeGoals == 0:
                    Line(tile, strings.Get("Places.LifeGoalsNone"));
                    break;
                case PlaceRules.LifeGoals:
                    Line(tile, strings.Get(openLifeGoals == 1 ? "Places.LifeGoal" : "Places.LifeGoals", openLifeGoals));
                    break;
                case PlaceRules.Projects:
                    Line(tile, strings.Get("Places.ProjectsLine", items.Count, items.Count(task => task.BoardColumn == ProjectRules.Doing)));
                    break;
                case PlaceRules.Wants when ready > 0:
                    Number(tile, ready, strings.Get("Places.WantsReady"));
                    break;
                case PlaceRules.Wants:
                    Line(tile, strings.Get("Places.WantsCooling", wantStates.Count(state => state == WantState.Cooling)));
                    break;
                case PlaceRules.Tally when tallyMinutes == 0:
                    Line(tile, strings.Get("Places.TallyNone"));
                    break;
                case PlaceRules.Tally:
                    Show(tile, PlaceTileLook.Tally, string.Empty, strings.Get("Places.TallyLine", labels.Duration(tallyMinutes)));
                    tile.Parts = [.. todays.Select(group => ((double)group.Minutes, labels.Brush(group.Key!)))];
                    break;
                case PlaceRules.Reviews when LetterWaiting(reviews.All(), today):
                    Show(tile, PlaceTileLook.Letter, string.Empty, strings.Get("Places.Letter"));
                    break;
                case PlaceRules.Reviews:
                    Line(tile, strings.Get("Places.ReviewsLine"));
                    break;
                case PlaceRules.Stats:
                    Line(tile, strings.Get("Places.StatsLine", StatsRules.WeeksDone(all, today, count: 1)[^1].Done));
                    break;
                case PlaceRules.Archive:
                    Line(tile, strings.Get("Places.ArchiveLine", ArchiveRules.Search(all, string.Empty).Count));
                    break;
            }
        }

        ShowPins();
    }

    /// <summary>
    /// Last week's review has a letter from a Claude routine and the owner has not started the review
    /// yet (the phone's Places hub reads it the same way).
    /// </summary>
    public static bool LetterWaiting(IEnumerable<ReviewItem> reviews, DateOnly today)
    {
        var lastWeek = ReviewLookBack.PreviousStart(ReviewRules.Weekly, ReviewRules.PeriodStart(ReviewRules.Weekly, today));
        var review = reviews.FirstOrDefault(review => !review.Deleted && review.Kind == ReviewRules.Weekly && review.PeriodStart == lastWeek);
        if (review is null || string.IsNullOrWhiteSpace(review.Summary))
        {
            return false;
        }

        var started = review.Mood is not null || review.Energy is not null || review.Reflections.Any(reflection => !string.IsNullOrWhiteSpace(reflection.Answer));
        return !started;
    }

    [RelayCommand]
    private void ToggleEditing() => IsEditing = !IsEditing;

    /// <summary>Leaves Edit, for instance when the page is left.</summary>
    public void StopEditing() => IsEditing = false;

    partial void OnIsEditingChanged(bool value) => ShowPins();

    // A click on a tile: open the place, or while editing pin or unpin it (the last pin stays).
    private void Activate(PlaceTileViewModel tile)
    {
        if (IsEditing)
        {
            places.Toggle(tile.Id);
        }
        else
        {
            places.Open(tile.Id);
        }
    }

    private void ShowPins()
    {
        var count = places.Pinned.Count;
        foreach (var tile in Tiles)
        {
            tile.IsPinned = places.IsPinned(tile.Id);
            tile.IsEditing = IsEditing;
            tile.CanToggle = !(tile.IsPinned && count <= 1);
        }

        Hint = IsEditing
            ? strings.Get("Places.EditHint", count)
            : strings.Get("Places.InSidebar", string.Join(", ", places.Pinned.Select(entry => entry.Label)));
    }

    private void Ring(PlaceTileViewModel tile, int done, int total)
    {
        Show(tile, PlaceTileLook.Ring, done.ToString(System.Globalization.CultureInfo.CurrentCulture), strings.Get("Places.OutOf", total));
        tile.Fraction = total == 0 ? 0 : (double)done / total;
    }

    private static void Number(PlaceTileViewModel tile, int value, string word) =>
        Show(tile, PlaceTileLook.Number, value.ToString(System.Globalization.CultureInfo.CurrentCulture), word);

    private static void Line(PlaceTileViewModel tile, string text) => Show(tile, PlaceTileLook.Line, string.Empty, text);

    private static void Show(PlaceTileViewModel tile, PlaceTileLook look, string number, string detail)
    {
        tile.Look = look;
        tile.Number = number;
        tile.Detail = detail;
        if (look != PlaceTileLook.Ring)
        {
            tile.Fraction = 0;
        }

        if (look != PlaceTileLook.Tally)
        {
            tile.Parts = [];
        }
    }
}
