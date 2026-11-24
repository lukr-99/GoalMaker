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

    // The board as big as the owner's are getting (LargeBoard): what the owner does by hand (typing,
    // filters, the who-made-it switch, switching projects, the quick search) never reads the replica
    // again; only a change does, once per burst. Timings live in the explicit ProjectsSpeedReport.
    // The search over every project stops drawing at its cap and says how many more there are.
    [Fact]
    public void OnALargeBoardOnlyAChangeReadsTheReplicaAndTheResultsStopAtTheirCap()
    {
        LargeBoard.Fill(planner);
        var page = Page();
        var big = LargeBoard.Big(planner);
        page.Select(big.Id);
        var reads = page.ReplicaReads;
        void Change(Action change)
        {
            change();
            planner.Time.Advance(TimeSpan.FromMilliseconds(150));
        }

        foreach (var length in Enumerable.Range(1, 11))
        {
            Change(() => page.BoardQuery = "sync widget"[..length]);
        }

        Change(() => page.SearchesEverywhere = false);
        Change(() => page.BoardQuery = string.Empty);
        Change(() => page.Filters.SelectedArea = page.Filters.AreaChoices[1]);
        Change(() => page.Filters.SelectedArea = page.Filters.AreaChoices[0]);
        Change(() => page.MadeByFilter = ProjectRules.Claude);
        Change(() => page.MadeByFilter = ProjectRules.Everyone);
        Change(() => page.TypeChip = ProjectRules.Bug);
        Change(() => page.TypeChip = null);
        foreach (var project in planner.Projects.All().Take(5))
        {
            Change(() => page.Select(project.Id));
        }

        Change(() => page.PaneQuery = "project");
        Assert.Equal(reads, page.ReplicaReads);

        // A burst of changes in one transaction is one read.
        planner.Replica.InTransaction(() =>
        {
            foreach (var item in planner.Tasks.All().Where(task => task.ProjectId == big.Id).Take(20))
            {
                planner.Tasks.SetPriority(item.Id, ProjectRules.Low);
            }
        });
        Assert.Equal(reads + 1, page.ReplicaReads);

        Change(() => page.SearchesEverywhere = true);
        Change(() => page.BoardQuery = "sync");
        Assert.Equal(200, page.Results.OfType<ItemResultViewModel>().Count());
        Assert.IsType<ItemResultViewModel>(page.Results[^1]);
        Assert.NotEqual(string.Empty, page.MoreFoundText);
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
