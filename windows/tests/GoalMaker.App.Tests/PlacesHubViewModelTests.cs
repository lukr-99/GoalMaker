using GoalMaker.App.ViewModels;
using GoalMaker.Core.Planning;
using GoalMaker.Infrastructure.Sync;

namespace GoalMaker.App.Tests;

/// <summary>
/// The Places page that All places opens (ADR 0014) over a real replica: a live tile for every place
/// with the same numbers the places show, the pins marked, and Edit, where a click pins or unpins.
/// </summary>
public sealed class PlacesHubViewModelTests : IDisposable
{
    // Friday 18 September 2026; the week runs from Monday the 14th, last week from the 7th.
    private static readonly DateOnly Today = new(2026, 9, 18);
    private readonly TestPlanner planner = new();
    private readonly PlacesViewModel places;

    public PlacesHubViewModelTests()
    {
        planner.Settings.PinnedPlaces = ["today", "tomorrow", "inbox", "projects"];
        places = new PlacesViewModel(planner.Settings, planner.Strings);
    }

    public void Dispose() => planner.Dispose();

    [Fact]
    public void EveryPlaceHasATileInTheOrderAllPlacesListsThem()
    {
        var hub = Hub();

        Assert.Equal(
            ["today", "tomorrow", "inbox", "calendar", "habits", "goals", "life-goals", "projects", "wants", "tally", "reviews", "stats", "archive"],
            hub.Tiles.Select(tile => tile.Id));
        Assert.Equal("Nav.Habits", Tile(hub, "habits").Title);
    }

    [Fact]
    public void TheTaskPlacesCountWhatTheirListsShow()
    {
        Planned("Call the bank", Today);
        var stretch = Planned("Stretch", Today);
        planner.Tasks.SetDone(stretch.Id, true);
        Planned("Pack the gym bag", Today.AddDays(1));
        Planned("Dentist", Today.AddDays(4));
        Planned("Too far off", Today.AddDays(9));
        planner.Tasks.Add("Read about sourdough");
        var project = planner.Projects.Add(new ProjectDraft("GoalMaker"))!;
        var item = planner.Tasks.Add("Fix the sidebar")!;
        planner.Tasks.SetProject(item.Id, project.Id);
        planner.Tasks.SetBoardColumn(item.Id, ProjectRules.Doing);
        planner.Tasks.SetProject(planner.Tasks.Add("Write the docs")!.Id, project.Id);

        var hub = Hub();

        var today = Tile(hub, "today");
        Assert.Equal(PlaceTileLook.Ring, today.Look);
        Assert.Equal(("1", "Places.OutOf(2)"), (today.Number, today.Detail));
        Assert.Equal(0.5, today.Fraction);
        Assert.Equal("Places.TomorrowLine(1)", Tile(hub, "tomorrow").Detail);
        var inbox = Tile(hub, "inbox");
        Assert.Equal(PlaceTileLook.Number, inbox.Look);
        Assert.Equal(("1", "Places.InboxWord"), (inbox.Number, inbox.Detail));
        Assert.Equal("Places.CalendarLine(2)", Tile(hub, "calendar").Detail);
        Assert.Equal("Places.ProjectsLine(2,1)", Tile(hub, "projects").Detail);
        Assert.Equal("Places.ArchiveLine(1)", Tile(hub, "archive").Detail);
        Assert.Equal(PlaceTileLook.Line, Tile(hub, "reviews").Look);
    }

    [Fact]
    public void WithNothingDueHabitsAndGoalsSaySoAndOtherwiseShowRings()
    {
        var hub = Hub();
        Assert.Equal((PlaceTileLook.Line, "Places.HabitsNone"), (Tile(hub, "habits").Look, Tile(hub, "habits").Detail));
        Assert.Equal((PlaceTileLook.Line, "Places.GoalsNone"), (Tile(hub, "goals").Look, Tile(hub, "goals").Detail));

        var read = planner.Habits.Add(new HabitDraft("Read", Today))!;
        planner.Habits.Add(new HabitDraft("Water", Today) { Measure = HabitRules.Count, Target = 8, Unit = "glasses" });
        planner.Habits.CheckIn(read.Id, Today);
        var week = new DateOnly(2026, 9, 14);
        var hit = planner.Goals.Add(new GoalDraft("Book the race", GoalHorizon.Week, week))!;
        planner.Goals.SetStatus(hit.Id, GoalRules.Done);
        planner.Goals.Add(new GoalDraft("Buy shoes", GoalHorizon.Week, week));
        planner.Goals.Add(new GoalDraft("A month one", GoalHorizon.Month, new DateOnly(2026, 9, 1)));

        // The tiles follow the lists as they change, without a new page.
        var habits = Tile(hub, "habits");
        Assert.Equal((PlaceTileLook.Ring, "1", "Places.OutOf(2)"), (habits.Look, habits.Number, habits.Detail));
        var goals = Tile(hub, "goals");
        Assert.Equal((PlaceTileLook.Ring, "1", "Places.OutOf(2)", 0.5), (goals.Look, goals.Number, goals.Detail, goals.Fraction));
    }

    [Fact]
    public void LifeGoalsCountTheOpenOnesOrAskWhatYouWant()
    {
        var hub = Hub();
        Assert.Equal((PlaceTileLook.Line, "Places.LifeGoalsNone"), (Tile(hub, "life-goals").Look, Tile(hub, "life-goals").Detail));

        planner.LifeGoals.Add(new LifeGoalDraft("Own an Audi R8", "Proof"));
        Assert.Equal("Places.LifeGoal(1)", Tile(hub, "life-goals").Detail);

        var boat = planner.LifeGoals.Add(new LifeGoalDraft("Sail to Greece", "The sea"))!;
        planner.LifeGoals.Add(new LifeGoalDraft("Run a marathon", "To know I can"));
        planner.LifeGoals.Achieve(boat.Id);

        Assert.Equal("Places.LifeGoals(2)", Tile(hub, "life-goals").Detail);
        Assert.Equal("Nav.LifeGoals", Tile(hub, "life-goals").Title);
    }

    [Fact]
    public void ReadyWantsAreABigNumberAndCoolingOnesALine()
    {
        planner.Wants.Add(new WantDraft("Lamp", "Dark desk", PickedDays: 30));
        var hub = Hub();
        Assert.Equal((PlaceTileLook.Line, "Places.WantsCooling(1)"), (Tile(hub, "wants").Look, Tile(hub, "wants").Detail));

        planner.Wants.Add(new WantDraft("Kindle", "Reading at night", PickedDays: 0));

        var wants = Tile(hub, "wants");
        Assert.Equal((PlaceTileLook.Number, "1", "Places.WantsReady"), (wants.Look, wants.Number, wants.Detail));
    }

    [Fact]
    public void ANeedIsNeitherAReadyWantNorACoolingOne()
    {
        planner.Wants.Add(new WantDraft("Lamp", "Dark desk", PickedDays: 30));
        planner.Wants.Add(new WantDraft("Winter tyres", string.Empty, Price: 12900, Kind: WantRules.Need));

        var wants = Tile(Hub(), "wants");

        Assert.Equal((PlaceTileLook.Line, "Places.WantsCooling(1)"), (wants.Look, wants.Detail));
    }

    [Fact]
    public void TallyIsTodaysBarFromEveryDevice()
    {
        var hub = Hub();
        Assert.Equal((PlaceTileLook.Line, "Places.TallyNone"), (Tile(hub, "tally").Look, Tile(hub, "tally").Detail));

        planner.TallyDay(Today, TallyRules.Pc, "coding", 90);
        planner.TallyDay(Today, TallyRules.Phone, "video", 30);
        planner.TallyDay(Today.AddDays(-1), TallyRules.Pc, "coding", 500);

        var tally = Tile(hub, "tally");
        Assert.Equal(PlaceTileLook.Tally, tally.Look);
        Assert.Equal("Places.TallyLine(Tally.Hours(2))", tally.Detail);
        Assert.Equal([90d, 30d], tally.Parts.Select(part => part.Amount));
    }

    [Fact]
    public void LastWeeksLetterMakesReviewsTheHeroUntilTheReviewIsStarted()
    {
        var review = planner.Reviews.Open(ReviewRules.Weekly, new DateOnly(2026, 9, 7))!;
        planner.Reviews.SetSummary(review.Id, "# Your week\nA good one.");
        var hub = Hub();

        var reviews = Tile(hub, "reviews");
        Assert.True(reviews.IsLetter);
        Assert.Equal("Places.Letter", reviews.Detail);

        planner.Reviews.SetMood(review.Id, 4);

        Assert.False(reviews.IsLetter);
        Assert.Equal("Places.ReviewsLine", reviews.Detail);
    }

    [Fact]
    public void AClickOpensThePlaceAndThePinsAreMarked()
    {
        var hub = Hub();
        string? opened = null;
        places.PlaceChosen += (_, place) => opened = place;

        Tile(hub, "stats").ActivateCommand.Execute(null);

        Assert.Equal("stats", opened);
        Assert.Equal("Places.InSidebar(Nav.Today, Nav.Tomorrow, Nav.Inbox, Nav.Projects)", hub.Hint);
        Assert.True(Tile(hub, "inbox").ShowsPinMark);
        Assert.False(Tile(hub, "stats").IsPinned);
        Assert.Equal(["today", "tomorrow", "inbox", "projects"], planner.Settings.PinnedPlaces);
    }

    [Fact]
    public void InEditAClickPinsOrUnpinsAndTheSidebarFollows()
    {
        var hub = Hub();
        var rebuilt = 0;
        places.PinsChanged += (_, _) => rebuilt++;
        string? opened = null;
        places.PlaceChosen += (_, place) => opened = place;

        hub.ToggleEditingCommand.Execute(null);
        Assert.Equal("Places.Done", hub.EditLabel);
        Assert.Equal("Places.EditHint(4)", hub.Hint);
        Assert.False(Tile(hub, "today").ShowsPinMark);
        Assert.Equal("Nav.Stats, Places.StatsLine(0), Places.NotPinned", Tile(hub, "stats").AutomationName);

        Tile(hub, "stats").ActivateCommand.Execute(null);
        Tile(hub, "inbox").ActivateCommand.Execute(null);

        Assert.Null(opened);
        Assert.Equal(["today", "tomorrow", "projects", "stats"], planner.Settings.PinnedPlaces);
        Assert.True(Tile(hub, "stats").IsPinned);
        Assert.False(Tile(hub, "inbox").IsPinned);
        Assert.Equal(2, rebuilt);

        hub.StopEditing();
        Assert.Equal("Places.Edit", hub.EditLabel);
        Assert.Equal("Places.InSidebar(Nav.Today, Nav.Tomorrow, Nav.Projects, Nav.Stats)", hub.Hint);
    }

    [Fact]
    public void ThePcHasNoPinLimitButTheLastPinStays()
    {
        var hub = Hub();
        hub.ToggleEditingCommand.Execute(null);
        foreach (var tile in hub.Tiles.Where(tile => !tile.IsPinned).ToList())
        {
            tile.ActivateCommand.Execute(null);
        }

        Assert.Equal(13, planner.Settings.PinnedPlaces.Count);
        Assert.All(hub.Tiles, tile => Assert.True(tile.IsActionable));

        foreach (var tile in hub.Tiles.Where(tile => tile.Id != "goals"))
        {
            tile.ActivateCommand.Execute(null);
        }

        var last = Tile(hub, "goals");
        Assert.Equal(["goals"], planner.Settings.PinnedPlaces);
        Assert.False(last.CanToggle);
        Assert.False(last.IsActionable);
        last.ActivateCommand.Execute(null);
        Assert.Equal(["goals"], planner.Settings.PinnedPlaces);

        // Outside Edit the last pin still opens its place.
        hub.StopEditing();
        Assert.True(last.IsActionable);
    }

    private static PlaceTileViewModel Tile(PlacesHubViewModel hub, string id) => hub.Tiles.Single(tile => tile.Id == id);

    private PlacesHubViewModel Hub()
    {
        var defaults = ContractResources.TallyDefaults();
        var habitsPage = new HabitsViewModel(planner.Habits, planner.Goals, planner.Settings, planner.Strings, planner.Time, () => true, action => action());
        return new PlacesHubViewModel(
            places,
            planner.Tasks,
            habitsPage,
            planner.Habits,
            planner.Goals,
            planner.LifeGoals,
            planner.Reviews,
            planner.Wants,
            planner.Tally,
            own => new TallyLabels(defaults, own, planner.Strings, _ => null),
            planner.Settings,
            planner.Strings,
            planner.Time,
            action => action());
    }

    private TaskItem Planned(string title, DateOnly day)
    {
        var task = planner.Tasks.Add(title)!;
        planner.Tasks.Plan(task.Id, day);
        return task;
    }
}
