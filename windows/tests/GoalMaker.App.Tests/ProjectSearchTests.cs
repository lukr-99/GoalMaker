using GoalMaker.App.ViewModels;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>
/// The Projects page's search box (docs/projects.md, "Searching items"): every project by default, or
/// this project's board; results by project and column with counts and the matched words lit up;
/// filter words and chips; recent searches; and a way out when nothing is found.
/// </summary>
public sealed class ProjectSearchTests : IDisposable
{
    private readonly TestPlanner planner = new();
    private readonly List<string> opened = [];

    public void Dispose() => planner.Dispose();

    [Fact]
    public void ItLooksInEveryProjectAndGroupsTheResultsByProjectAndColumnWithCounts()
    {
        var page = TwoProjects();

        Search(page, "board");

        Assert.True(page.ShowsResults);
        Assert.Equal(
            ["[GoalMaker 2]", "[Projects.Todo 1]", "Ship the board", "[Projects.Doing 1]", "Fix the board sync", "[Thesis 1]", "[Projects.Backlog 1]", "Draw the board"],
            Entries(page));
        Assert.Equal("Projects.ResultsCount(3,2)", page.ResultsText);
        Assert.False(page.IsNoResults);
    }

    [Fact]
    public void WordsMatchInAnyOrderIgnoringCaseAndAccentsAndTheMatchedPartsLightUp()
    {
        var page = TwoProjects();
        planner.Tasks.SetProject(planner.Tasks.Add("Zrušit kůň plán")!.Id, planner.Projects.All()[0].Id);

        Search(page, "KUN zrusit");

        var result = Assert.Single(page.Results.OfType<ItemResultViewModel>());
        Assert.Equal([("Zrušit", true), (" ", false), ("kůň", true), (" plán", false)], result.TitleParts.Select(part => (part.Text, part.IsMatch)));
    }

    [Fact]
    public void AnItemIdFindsThatItem()
    {
        var page = TwoProjects();
        var goal = planner.Projects.All().Single(project => project.Name == "GoalMaker");
        planner.Projects.SetItemKey(goal.Id, "GM");
        Number(planner.Task("Ship the board").Id, 12);

        Search(page, "gm-12");

        Assert.Equal("Ship the board", Assert.Single(page.Results.OfType<ItemResultViewModel>()).Title);
        Assert.Equal("GM-12", page.Results.OfType<ItemResultViewModel>().Single().ItemId);
    }

    [Fact]
    public void FilterWordsAndChipsNarrowTheSearch()
    {
        var page = TwoProjects();
        var goal = planner.Projects.All().Single(project => project.Name == "GoalMaker");
        planner.Tasks.SetTags(planner.Task("Ship the board").Id, ["release"]);
        var m3 = planner.Projects.AddMilestone(goal.Id, "M3")!;
        planner.Tasks.SetMilestone(planner.Task("Fix the board sync").Id, m3.Id);
        planner.Tasks.SetBoardColumn(planner.Task("Draw the board").Id, ProjectRules.Done);

        Assert.Equal(["Ship the board"], Found(page, "board #release"));
        Assert.Equal(["Fix the board sync"], Found(page, "type:bug"));
        Assert.Equal(["Fix the board sync"], Found(page, "m:m3"));
        Assert.Equal(["Draw the board"], Found(page, "board is:done"));
        Assert.Equal(["Ship the board"], Found(page, "p:high"));
        Assert.True(page.CanClearFilters);

        page.StatusChip = "open";
        Assert.Equal(["Ship the board", "Fix the board sync"], Found(page, "board"));
        page.TypeChip = ProjectRules.Bug;
        Assert.Equal(["Fix the board sync"], Found(page, "board"));
        Assert.Contains(page.MilestoneChips, chip => chip.Id == "M3");
        Assert.True(page.CanClearFilters);

        page.ClearFiltersCommand.Execute(null);
        Assert.Null(page.TypeChip);
        Assert.Equal(3, Found(page, "board").Count);
    }

    [Fact]
    public void NothingFoundOffersToLookEverywhereOrToLetGoOfTheFilters()
    {
        var page = TwoProjects();
        page.Select(planner.Projects.All().Single(project => project.Name == "Thesis").Id);
        page.SearchesEverywhere = false;

        Search(page, "sync");
        Assert.True(page.IsNoResults);
        Assert.Equal("Projects.NoResultsIn(Thesis)", page.NoResultsText);
        Assert.True(page.SearchesHere);

        page.WidenSearchCommand.Execute(null);
        Assert.False(page.IsNoResults);
        Assert.Equal(["Fix the board sync"], page.Results.OfType<ItemResultViewModel>().Select(result => result.Title));

        Search(page, "sync is:dropped");
        Assert.True(page.IsNoResults);
        Assert.Equal("Projects.NoResultsAnywhere", page.NoResultsText);
        Assert.True(page.CanClearFilters);
        page.ClearFiltersCommand.Execute(null);
        planner.Time.Advance(TimeSpan.FromMilliseconds(150));
        Assert.Equal("sync", page.BoardQuery);
        Assert.False(page.IsNoResults);
    }

    [Fact]
    public void AFoundItemTakesTheBoardToItsCardWithTheSearchNarrowedToItsProject()
    {
        var page = TwoProjects();
        page.Select(planner.Projects.All().Single(project => project.Name == "Thesis").Id);
        Column(page, ProjectRules.Doing).FoldCommand.Execute(null);
        BoardItemViewModel? shown = null;
        page.CardShown += (_, card) => shown = card;

        Search(page, "sync");
        page.Results.OfType<ItemResultViewModel>().Single().JumpCommand.Execute(null);

        Assert.Equal("GoalMaker", page.ProjectName);
        Assert.False(page.ShowsResults);
        Assert.True(page.SearchesHere);
        Assert.Equal("sync", page.BoardQuery);
        Assert.False(Column(page, ProjectRules.Doing).IsFolded);
        Assert.Equal("Fix the board sync", shown?.Title);
        Assert.Equal(["sync"], page.RecentSearches);
        Assert.Empty(opened);

        page.ClearSearchCommand.Execute(null);
        Assert.True(page.SearchesEverywhere);
        Assert.Equal(2, page.Columns.Sum(column => column.Items.Count));
    }

    [Fact]
    public void AFoundItemOffTheBoardOpensOnItsOwnPage()
    {
        var page = TwoProjects();
        var done = planner.Task("Ship the board");
        planner.Tasks.SetBoardColumn(done.Id, ProjectRules.Done);
        planner.Tasks.SetBoardArchived(done.Id, true);

        Search(page, "ship");
        var result = page.Results.OfType<ItemResultViewModel>().Single();
        Assert.Equal("Projects.ResultPlace(GoalMaker,Projects.OffBoard)", result.Place);
        Assert.Contains(page.Results.OfType<ResultHeadingViewModel>(), heading => heading.Title == "Projects.OffBoard");
        result.JumpCommand.Execute(null);

        Assert.Equal([done.Id], opened);
    }

    [Fact]
    public void EnterRemembersTheSearchAndARecentSearchRunsAgain()
    {
        var page = TwoProjects();
        BoardItemViewModel? shown = null;
        page.CardShown += (_, card) => shown = card;

        page.BoardQuery = "draw";
        page.OpenFirstResultCommand.Execute(null);
        Assert.Equal("Draw the board", shown?.Title);
        page.ClearSearchCommand.Execute(null);
        page.BoardQuery = "ship";
        page.OpenFirstResultCommand.Execute(null);
        page.ClearSearchCommand.Execute(null);

        Assert.Equal(["ship", "draw"], page.RecentSearches);
        Assert.Equal(["ship", "draw"], planner.Settings.RecentSearches);
        page.IsSearchFocused = true;
        Assert.True(page.ShowsRecent);

        page.UseRecentCommand.Execute("draw");
        Assert.Equal("draw", page.BoardQuery);
        Assert.Equal(["Draw the board"], page.Results.OfType<ItemResultViewModel>().Select(result => result.Title));
        Assert.False(page.ShowsRecent);
    }

    [Fact]
    public void TheSearchIsThereWithNoProjectOnShow()
    {
        var page = TwoProjects();
        var home = planner.Areas.FindOrCreate("Home")!;
        page.Filters.SelectedArea = page.Filters.AreaChoices.Single(area => area.Id == home.Id);

        Assert.True(page.IsFilteredAway);
        Assert.False(page.ShowsProject);
        Assert.True(page.CanSearch);

        page.ClearFiltersCommand.Execute(null);
        Search(page, "board");
        Assert.True(page.ShowsResults);
        Assert.Equal(3, page.Results.OfType<ItemResultViewModel>().Count());

        page.EditCommand.Execute(null);
        Assert.False(page.CanSearch);
    }

    private List<string> Found(ProjectsViewModel page, string query)
    {
        Search(page, query);
        return [.. page.Results.OfType<ItemResultViewModel>().Select(result => result.Title)];
    }

    private void Search(ProjectsViewModel page, string query)
    {
        page.BoardQuery = query;
        planner.Time.Advance(TimeSpan.FromMilliseconds(150));
    }

    private void Number(string id, int number)
    {
        var row = planner.Replica.Get("tasks", id)!;
        row["item_number"] = number;
        planner.Replica.Put("tasks", row);
    }

    // GoalMaker with a high-priority task in To do and a bug in Doing; Thesis with an idea in Backlog.
    private ProjectsViewModel TwoProjects()
    {
        var goal = planner.Projects.Add(new ProjectDraft("GoalMaker"))!;
        var thesis = planner.Projects.Add(new ProjectDraft("Thesis"))!;
        void Item(string title, ProjectItem project, string type, string column)
        {
            var task = planner.Tasks.Add(title)!;
            planner.Tasks.SetProject(task.Id, project.Id, type);
            planner.Tasks.SetBoardColumn(task.Id, column);
        }

        Item("Ship the board", goal, ProjectRules.Task, ProjectRules.Todo);
        Item("Fix the board sync", goal, ProjectRules.Bug, ProjectRules.Doing);
        Item("Draw the board", thesis, ProjectRules.Idea, ProjectRules.Backlog);
        planner.Tasks.SetPriority(planner.Task("Ship the board").Id, ProjectRules.High);
        var page = new ProjectsViewModel(
            planner.Projects, planner.Tasks, planner.Areas, planner.Tags, planner.Settings, planner.Strings, _ => null, opened.Add, action => action(), planner.Time, _ => { });
        page.Select(goal.Id);
        return page;
    }

    private static BoardColumnViewModel Column(ProjectsViewModel page, string column) => page.Columns.Single(candidate => candidate.Column == column);

    private static IEnumerable<string> Entries(ProjectsViewModel page) => page.Results.Select(entry => entry switch
    {
        ResultHeadingViewModel heading => $"[{heading.Title} {heading.Count}]",
        ItemResultViewModel result => result.Title,
        _ => "?",
    });
}
