using GoalMaker.App.ViewModels;
using GoalMaker.Core.Composer;

namespace GoalMaker.App.Tests;

/// <summary>
/// The All tasks page on Windows (docs/all-tasks.md): a task filed into an area without a day shows under its
/// area, the search and filter narrow the page, and project items show only when asked for.
/// </summary>
public sealed class AllTasksViewModelTests : IDisposable
{
    private readonly TestPlanner planner = new();
    private readonly List<string> opened = [];

    public void Dispose() => planner.Dispose();

    private AllTasksViewModel Page() => new(
        planner.Tasks, planner.Areas, planner.Tags, planner.Projects, planner.Settings, planner.Strings, _ => null,
        planner.Time, action => action(), opened.Add);

    private void Add(string line) => planner.Tasks.Add(ComposerParser.Parse(line, planner.Time.GetLocalNow().DateTime));

    private static List<string> Shown(AllTasksViewModel page) =>
        [.. page.Groups.Select(group => $"{group.Name}: {string.Join(", ", group.Rows.Select(row => row.Title))}")];

    [Fact]
    public void ATaskFiledIntoAnAreaWithoutADayShowsUnderItsArea()
    {
        Add("Extend the ISIC card @School");
        Add("See internship hours @School");
        Add("Call mum");
        var page = Page();

        Assert.Equal(["School: Extend the ISIC card, See internship hours", "AllTasks.NoArea: Call mum"], Shown(page));
        Assert.False(page.IsEmpty);
    }

    [Fact]
    public void TheSearchAndTheFilterNarrowThePage()
    {
        Add("Studijní: zrušit dipl. práci @School");
        Add("Studijní: prodloužit ISIC @School");
        Add("Call mum");
        var page = Page();

        page.Query = "zrusit";
        Assert.Equal(["School: Studijní: zrušit dipl. práci"], Shown(page));

        page.Query = "dentist";
        Assert.True(page.IsEmpty);
        Assert.Equal("AllTasks.NoMatch", page.EmptyText);

        page.Query = string.Empty;
        page.Filters.SelectedArea = page.Filters.AreaChoices.Single(choice => choice.Id is not null);
        Assert.Equal(["School: Studijní: prodloužit ISIC, Studijní: zrušit dipl. práci"], Shown(page));
    }

    [Fact]
    public void ProjectItemsShowOnlyWhenAskedFor()
    {
        Add("Book a room @Work");
        Add("Fix the build +GoalMaker");
        var page = Page();
        Assert.Equal(["Work: Book a room"], Shown(page));

        page.ShowProjectItems = true;
        Assert.Equal(["Work: Book a room", "AllTasks.NoArea: Fix the build"], Shown(page));
    }

    [Fact]
    public void ARowOpensItsTaskAndCompletingItTakesItOff()
    {
        Add("Hand in the form @School");
        var page = Page();
        var row = page.Groups.Single().Rows.Single();

        row.OpenCommand.Execute(null);
        Assert.Single(opened);

        row.CompleteCommand.Execute(null);
        Assert.True(page.IsEmpty);
        Assert.Equal("AllTasks.Empty", page.EmptyText);
    }
}
