using System.Diagnostics;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>
/// The project list and its quick search (GM-122): pinned projects first, then the rest by area, paused
/// and done ones folded away at the end; the latest opened project shown first; a quick search that
/// finds projects and items in any project and takes the board to a found item; the board's search
/// waiting for a pause in typing; rows and cards kept from one redraw to the next (GM-121).
/// </summary>
public sealed class ProjectPaneTests : IDisposable
{
    private readonly TestPlanner planner = new();
    private readonly List<string> opened = [];

    public void Dispose() => planner.Dispose();

    [Fact]
    public void PinnedProjectsComeFirstThenEachAreaAndPausedOnesFoldAwayAtTheEnd()
    {
        var work = planner.Areas.FindOrCreate("Work")!;
        var home = planner.Areas.FindOrCreate("Home")!;
        planner.Projects.Add(new ProjectDraft("GoalMaker") { AreaId = work.Id });
        planner.Projects.Add(new ProjectDraft("Kiln") { AreaId = work.Id });
        planner.Projects.Add(new ProjectDraft("Garden") { AreaId = home.Id });
        planner.Projects.Add(new ProjectDraft("Loose ends"));
        planner.Projects.Add(new ProjectDraft("Old thing") { AreaId = work.Id, Status = ProjectRules.Paused });
        var page = Page();

        Assert.Equal(["[Work 2]", "GoalMaker", "Kiln", "[Home 1]", "Garden", "[Projects.NoArea 1]", "Loose ends", "[Projects.Resting 1]"], Entries(page));

        var kiln = page.Projects.Single(row => row.Name == "Kiln");
        kiln.TogglePinCommand.Execute(null);
        Assert.True(kiln.IsPinned);
        Assert.Equal("Projects.Unpin", kiln.PinText);
        Assert.Equal(["[Projects.Pinned 1]", "Kiln", "[Work 1]", "GoalMaker"], Entries(page).Take(4));

        var resting = page.PaneEntries.OfType<ProjectGroupViewModel>().Single(group => group.CanFold);
        resting.FoldCommand.Execute(null);
        Assert.Equal(["[Projects.Resting 1]", "Old thing"], Entries(page).TakeLast(2));
        Assert.Equal(["Kiln"], planner.Settings.PinnedProjects.Select(id => planner.Projects.Get(id)!.Name));
    }

    [Fact]
    public void ARowSaysHowMuchIsOpenAndInDoing()
    {
        var page = WithItems();
        var row = Assert.Single(page.Projects);

        Assert.Equal((1, 1, 1, 3), (row.Backlog, row.Todo, row.Doing, row.Open));
        Assert.Equal("Projects.Load(3,1)", row.LoadText);
        Assert.True(row.IsSelected);
    }

    [Fact]
    public void TheLatestProjectOpenedIsTheOneShownFirst()
    {
        planner.Projects.Add(new ProjectDraft("GoalMaker"));
        var kiln = planner.Projects.Add(new ProjectDraft("Kiln"))!;
        Page().Select(kiln.Id);

        var again = Page();

        Assert.Equal("Kiln", again.ProjectName);
        Assert.Equal(kiln.Id, planner.Settings.RecentProjects[0]);
    }

    [Fact]
    public void TheQuickSearchInTheListFindsProjectsByNameOrKey()
    {
        var page = WithItems();
        var games = planner.Projects.Add(new ProjectDraft("Board games"))!;
        planner.Projects.SetItemKey(games.Id, "BG");

        Search(page, "board");
        Assert.Equal(["[Projects.Title 1]", "Board games"], Entries(page));
        Search(page, "bg");
        Assert.Equal(["[Projects.Title 1]", "Board games"], Entries(page));

        Search(page, "nothing like it");
        Assert.True(page.IsNothingFound);
        Assert.Empty(page.PaneEntries);
    }

    [Fact]
    public void EnterInTheQuickSearchOpensTheFirstProjectFoundAtOnce()
    {
        WithItems();
        var kiln = planner.Projects.Add(new ProjectDraft("Kiln"))!;
        var page = Page();

        page.PaneQuery = "kil";
        page.PickFirstCommand.Execute(null);

        Assert.Equal("Kiln", page.ProjectName);
        Assert.Equal(string.Empty, page.PaneQuery);
        Assert.Equal(kiln.Id, planner.Settings.RecentProjects[0]);
    }

    [Fact]
    public void TheBoardsSearchWaitsForAPauseInTypingAndAnEmptyBoxShowsEverythingAtOnce()
    {
        var page = WithItems();
        page.SearchesEverywhere = false;

        page.BoardQuery = "sync";
        Assert.Equal(3, Cards(page).Count);
        planner.Time.Advance(TimeSpan.FromMilliseconds(100));
        page.BoardQuery = "sync fi";
        planner.Time.Advance(TimeSpan.FromMilliseconds(100));
        Assert.Equal(3, Cards(page).Count);
        planner.Time.Advance(TimeSpan.FromMilliseconds(50));
        Assert.Equal(["Fix sync"], Cards(page).Select(card => card.Title));

        page.BoardQuery = string.Empty;
        Assert.Equal(3, Cards(page).Count);
    }

    [Fact]
    public void ARedrawKeepsTheRowsAndTheCardsThatDidNotChange()
    {
        var page = WithItems();
        var row = page.Projects.Single();
        var ship = Cards(page).Single(card => card.Title == "Ship the board");
        var widgets = Cards(page).Single(card => card.Title == "Widgets");

        planner.Tasks.SetPriority(planner.Task("Ship the board").Id, ProjectRules.Urgent);

        Assert.Same(row, page.Projects.Single());
        Assert.Same(widgets, Cards(page).Single(card => card.Title == "Widgets"));
        Assert.NotSame(ship, Cards(page).Single(card => card.Title == "Ship the board"));
        Assert.Equal("Projects.Urgent", Cards(page).Single(card => card.Title == "Ship the board").Priority);
    }

    // The board as big as the owner's are getting (LargeBoard): every change the owner makes by hand
    // is quick in the view model. The budget is loose, for a busy CI machine; ProjectsSpeedReport
    // measures the page as drawn.
    [Fact]
    public void OnALargeBoardSearchingFilteringAndSwitchingStayQuick()
    {
        LargeBoard.Fill(planner);
        var page = Page();
        var big = LargeBoard.Big(planner);
        page.Select(big.Id);
        var times = new List<double>();
        void Timed(Action change)
        {
            var watch = Stopwatch.StartNew();
            change();
            planner.Time.Advance(TimeSpan.FromMilliseconds(150));
            times.Add(watch.Elapsed.TotalMilliseconds);
        }

        foreach (var length in Enumerable.Range(1, 11))
        {
            Timed(() => page.BoardQuery = "sync widget"[..length]);
        }

        Timed(() => page.BoardQuery = string.Empty);
        Timed(() => page.Filters.SelectedArea = page.Filters.AreaChoices[1]);
        Timed(() => page.Filters.SelectedArea = page.Filters.AreaChoices[0]);
        Timed(() => page.MadeByFilter = ProjectRules.Claude);
        Timed(() => page.MadeByFilter = ProjectRules.Everyone);
        foreach (var project in planner.Projects.All().Take(5))
        {
            Timed(() => page.Select(project.Id));
        }

        Timed(() => page.PaneQuery = "sync");
        var median = times.Order().ElementAt(times.Count / 2);
        Assert.True(median < 50, $"The median change took {median} ms: {string.Join(", ", times.Select(time => time.ToString("F1", System.Globalization.CultureInfo.InvariantCulture)))}.");
    }

    private void Search(ProjectsViewModel page, string query)
    {
        page.PaneQuery = query;
        planner.Time.Advance(TimeSpan.FromMilliseconds(150));
    }

    // GoalMaker with an idea in Backlog, a task in To do and a bug in Doing.
    private ProjectsViewModel WithItems()
    {
        var page = Page();
        page.NewCommand.Execute(null);
        page.ProjectName = "GoalMaker";
        page.SaveCommand.Execute(null);
        foreach (var (title, type) in new[] { ("Widgets", ProjectRules.Idea), ("Ship the board", ProjectRules.Task), ("Fix sync", ProjectRules.Bug) })
        {
            page.NewItemType = type;
            page.NewItemTitle = title;
            page.AddItemCommand.Execute(null);
        }

        planner.Tasks.SetBoardColumn(planner.Task("Fix sync").Id, ProjectRules.Doing);
        return page;
    }

    private static List<BoardItemViewModel> Cards(ProjectsViewModel page) => [.. page.Columns.SelectMany(column => column.Items)];

    private static BoardColumnViewModel Column(ProjectsViewModel page, string column) => page.Columns.Single(candidate => candidate.Column == column);

    private static IEnumerable<string> Entries(ProjectsViewModel page) => page.PaneEntries.Select(entry => entry switch
    {
        ProjectGroupViewModel group => $"[{group.Title} {group.Count}]",
        ProjectRowViewModel row => row.Name,
        ItemResultViewModel result => result.Title,
        _ => "?",
    });

    private ProjectsViewModel Page() =>
        new(planner.Projects, planner.Tasks, planner.Areas, planner.Tags, planner.Settings, planner.Strings, _ => null, opened.Add, action => action(), planner.Time, _ => { });
}
