using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>
/// The Projects page's index (GM-121): each project's items and load, the search by words or id, the
/// search over every project, folded text kept from one index to the next, and how long it takes on a
/// board the size the owner's are heading for.
/// </summary>
public sealed class ProjectBoardIndexTests
{
    private static readonly ProjectItem Goal = new("p1", "GoalMaker") { ItemKey = "GM" };
    private static readonly ProjectItem Thesis = new("p2", "Thesis");
    private static readonly IReadOnlyDictionary<string, IReadOnlySet<string>> NoLinks = new Dictionary<string, IReadOnlySet<string>>();

    [Fact]
    public void ItHoldsEachProjectsItemsAndWhatItsOpenColumnsHold()
    {
        var index = ProjectBoardIndex.Build(
            [Goal, Thesis],
            [
                Item("a", "Ship the board", Goal, ProjectRules.Todo),
                Item("b", "Widgets", Goal, ProjectRules.Backlog),
                Item("c", "Sync", Goal, ProjectRules.Doing),
                Item("d", "Old", Goal, ProjectRules.Done) with { State = TaskState.Done },
                Item("e", "Gone", Goal, ProjectRules.Todo) with { State = TaskState.Dropped },
                Item("f", "Write", Thesis, ProjectRules.Todo),
                new TaskItem("g", "Not an item", TaskState.Open, false, "2026-09-01"),
            ],
            NoLinks);

        Assert.Equal(["a", "b", "c", "d", "e"], index.ItemsOf("p1").Select(item => item.Id));
        Assert.Equal(new ProjectLoad(1, 1, 1), index.LoadOf("p1"));
        Assert.Equal(3, index.LoadOf("p1").Open);
        Assert.Equal(ProjectLoad.None, index.LoadOf("nobody"));
        Assert.Same(Thesis, index.Project("p2"));
    }

    [Fact]
    public void TheSearchFindsEveryWordInTheTitleOrNotesIgnoringCaseAndAccents()
    {
        var item = Item("a", "Zrušit diplomovou práci", Goal, ProjectRules.Todo) with { Notes = "Studijní oddělení" };
        var index = ProjectBoardIndex.Build([Goal], [item], NoLinks);

        Assert.True(index.Finds(ProjectSearch.Of("zrusit PRACI"), item, inProject: true));
        Assert.True(index.Finds(ProjectSearch.Of("  studijni   praci "), item, inProject: true));
        Assert.False(index.Finds(ProjectSearch.Of("zrusit thesis"), item, inProject: true));
        Assert.True(index.Finds(ProjectSearch.Of("   "), item, inProject: true));
        Assert.True(ProjectSearch.Of(" ").IsEmpty);
    }

    [Fact]
    public void TheSearchFindsAnItemByItsIdOrByItsNumberInItsProject()
    {
        var item = Item("a", "Ship the board", Goal, ProjectRules.Todo) with { ItemNumber = 12 };
        var other = Item("b", "Write", Thesis, ProjectRules.Todo) with { ItemNumber = 12 };
        var index = ProjectBoardIndex.Build([Goal, Thesis], [item, other], NoLinks);

        Assert.True(index.Finds(ProjectSearch.Of("gm-12"), item, inProject: false));
        Assert.False(index.Finds(ProjectSearch.Of("GM-13"), item, inProject: false));
        Assert.True(index.Finds(ProjectSearch.Of("#12"), item, inProject: true));
        Assert.False(index.Finds(ProjectSearch.Of("#12"), item, inProject: false));
        Assert.True(index.Finds(ProjectSearch.Of("#12"), other, inProject: false));
    }

    [Fact]
    public void TheSearchOverEveryProjectPutsOpenItemsFirstAndNewestFirstAndStopsAtTheLimit()
    {
        var index = ProjectBoardIndex.Build(
            [Goal, Thesis],
            [
                Item("a", "board one", Goal, ProjectRules.Todo) with { CreatedAt = "2026-09-01" },
                Item("b", "board two", Thesis, ProjectRules.Todo) with { CreatedAt = "2026-09-03" },
                Item("c", "board done", Goal, ProjectRules.Done) with { State = TaskState.Done, CreatedAt = "2026-09-05" },
                Item("d", "board three", Goal, ProjectRules.Backlog) with { CreatedAt = "2026-09-02" },
                Item("e", "nothing here", Goal, ProjectRules.Backlog),
            ],
            NoLinks);

        Assert.Equal(["b", "d", "a", "c"], index.Search(ProjectSearch.Of("board"), 10, out var all).Select(item => item.Id));
        Assert.Equal(4, all);
        Assert.Equal(["b", "d"], index.Search(ProjectSearch.Of("board"), 2, out var total).Select(item => item.Id));
        Assert.Equal(4, total);
        Assert.Empty(index.Search(ProjectSearch.Everything, 10, out var none));
        Assert.Equal(0, none);
    }

    [Fact]
    public void FilterWordsNarrowTheSearchAndUnknownOnesAreSearchedAsWords()
    {
        var search = ProjectSearch.Of("Board #Release is:done TYPE:bug p:high m:M3 gm-12 is:nothing");

        Assert.Equal(["board", "is:nothing"], search.Words);
        Assert.Equal(["release"], search.Tags);
        Assert.Equal(["done"], search.States);
        Assert.Equal(["bug"], search.ItemTypes);
        Assert.Equal(["high"], search.Priorities);
        Assert.Equal(["m3"], search.Milestones);
        Assert.Equal(new ItemId("GM", 12), search.Id);
        Assert.True(search.HasFilters);
        Assert.False(ProjectSearch.Of("board #12").HasFilters);
        Assert.Equal(new ItemId(null, 12), ProjectSearch.Of("board #12").Id);
        Assert.Equal(["idea"], ProjectSearch.Of("is:idea").ItemTypes);
        Assert.True(ProjectSearch.Everything.With().IsEmpty);
        Assert.Equal(["urgent"], ProjectSearch.Everything.With(priority: "urgent").Priorities);
    }

    [Fact]
    public void TagsMilestonesAndStatesAreMatchedByTheirNames()
    {
        var item = Item("a", "Ship the board", Goal, ProjectRules.Todo) with { MilestoneId = "m3", Priority = ProjectRules.High, ItemType = ProjectRules.Bug };
        var index = ProjectBoardIndex.Build(
            [Goal],
            [item],
            new Dictionary<string, IReadOnlySet<string>> { ["a"] = new HashSet<string> { "t1" } },
            null,
            new Dictionary<string, string> { ["t1"] = "Release" },
            new Dictionary<string, string> { ["m3"] = "M3" });

        Assert.True(index.Finds(ProjectSearch.Of("#release m:m3 is:open type:bug p:high board"), item, inProject: true));
        Assert.False(index.Finds(ProjectSearch.Of("#other"), item, inProject: true));
        Assert.False(index.Finds(ProjectSearch.Of("is:done"), item, inProject: true));
        Assert.False(index.Finds(ProjectSearch.Of("m:m4"), item, inProject: true));
        Assert.False(index.Finds(ProjectSearch.Everything.With(itemType: ProjectRules.Idea), item, inProject: true));
        Assert.Equal(["a"], index.FindAll(ProjectSearch.Of("#release")).Select(found => found.Id));
    }

    [Fact]
    public void TheMatchedPartsAreFoundIgnoringCaseAndAccents()
    {
        Assert.Equal([(0, 6), (7, 3)], SearchHighlight.Ranges("Zrušit kůň", ["zrusit", "kun"]));
        Assert.Equal([(5, 5)], SearchHighlight.Ranges("Ship BOARD", ["board"]));
        Assert.Empty(SearchHighlight.Ranges("Ship", ["nothing"]));
        Assert.Equal([(3, 4)], SearchHighlight.Ranges("🚀 sync", ["sync"]));
    }

    [Fact]
    public void ANewIndexFoldsOnlyTheItemsWhoseTextChanged()
    {
        var kept = Item("a", "Příliš žluťoučký kůň", Goal, ProjectRules.Todo);
        var changed = Item("b", "Old title", Goal, ProjectRules.Todo);
        var first = ProjectBoardIndex.Build([Goal], [kept, changed], NoLinks);

        var second = ProjectBoardIndex.Build([Goal], [kept with { Priority = ProjectRules.High }, changed with { Title = "New title" }], NoLinks, first);

        Assert.True(second.Finds(ProjectSearch.Of("prilis kun"), kept, inProject: true));
        Assert.True(second.Finds(ProjectSearch.Of("new"), changed, inProject: true));
        Assert.False(second.Finds(ProjectSearch.Of("old"), changed with { Title = "New title" }, inProject: true));
    }

    [Fact]
    public void FoldingPlainTextGivesWhatTheFullFoldGives()
    {
        foreach (var text in new[] { "Ship The Board", "GM-12 #12 (sync)", "Příliš ŽLUŤOUČKÝ", "Ångström café", string.Empty })
        {
            var slow = new string(text.Normalize(System.Text.NormalizationForm.FormD)
                .Where(character => System.Globalization.CharUnicodeInfo.GetUnicodeCategory(character) is not (System.Globalization.UnicodeCategory.NonSpacingMark or System.Globalization.UnicodeCategory.SpacingCombiningMark or System.Globalization.UnicodeCategory.EnclosingMark))
                .ToArray()).ToLowerInvariant();
            Assert.Equal(slow, ArchiveRules.Fold(text));
        }
    }

    // 3,000 items in 40 projects: the index finds exactly what reading every item one by one finds, and
    // the next index folds only the items whose text changed. Timings live in the explicit
    // ProjectsSpeedReport, not in tests that run on busy machines.
    [Fact]
    public void OnALargeBoardTheIndexFindsWhatAPlainScanFinds()
    {
        var projects = Enumerable.Range(0, 40).Select(i => new ProjectItem($"p{i}", $"Project {i}")).ToList();
        var random = new Random(121);
        string[] words = ["sync", "widget", "board", "search", "filter", "calendar", "habit", "příliš", "žluťoučký", "kůň"];
        var tasks = Enumerable.Range(0, 3000)
            .Select(i => Item($"t{i}", string.Join(' ', Enumerable.Range(0, 5).Select(_ => words[random.Next(words.Length)])), projects[i % 40], ProjectRules.Todo)
                with
            { Notes = string.Join(' ', Enumerable.Range(0, 20).Select(_ => words[random.Next(words.Length)])) })
            .ToList();
        var index = ProjectBoardIndex.Build(projects, tasks, NoLinks);

        foreach (var query in new[] { "sync kun", "PŘÍLIŠ board", "habit widget filter", "nothing" })
        {
            var wanted = ProjectSearch.Of(query).Words;
            var scanned = tasks
                .Where(task => wanted.All(word => (ArchiveRules.Fold(task.Title) + "\n" + ArchiveRules.Fold(task.Notes)).Contains(word, StringComparison.Ordinal)))
                .Select(task => task.Id)
                .Order(StringComparer.Ordinal);
            Assert.Equal(scanned, index.FindAll(ProjectSearch.Of(query)).Select(task => task.Id).Order(StringComparer.Ordinal));
        }

        var changed = tasks[7] with { Title = "renamed only here" };
        var next = ProjectBoardIndex.Build(projects, [.. tasks.Take(7), changed, .. tasks.Skip(8)], NoLinks, index);
        Assert.Equal(["t7"], next.FindAll(ProjectSearch.Of("renamed")).Select(task => task.Id));
    }

    [Fact]
    public void AnyWhitespacePartsWordsAndQuotesKeepAPhraseTogether()
    {
        var search = ProjectSearch.Of("board\tsync\n  m:\"Q4 launch\" #\"to read\" \"exact phrase\"");

        Assert.Equal(["board", "sync", "exact phrase"], search.Words);
        Assert.Equal(["q4 launch"], search.Milestones);
        Assert.Equal(["to read"], search.Tags);
        Assert.Equal([("m:\"Q4 launch\"", "m:Q4 launch"), ("x", "x")], ProjectSearch.Tokens("  m:\"Q4 launch\"\u00a0x "));
    }

    private static TaskItem Item(string id, string title, ProjectItem project, string column) =>
        new(id, title, column == ProjectRules.Done ? TaskState.Done : TaskState.Open, false, "2026-09-01", ProjectId: project.Id, BoardColumn: column);
}
