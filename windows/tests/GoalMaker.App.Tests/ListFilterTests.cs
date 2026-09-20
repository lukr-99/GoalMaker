using GoalMaker.App.ViewModels;
using GoalMaker.Core.Composer;

namespace GoalMaker.App.Tests;

/// <summary>Filtering the lists by area and tag from the pickers above them (docs/lists.md, M2-11).</summary>
public sealed class ListFilterTests : IDisposable
{
    private static readonly DateOnly Today = new(2026, 9, 18);
    private readonly TestPlanner planner = new();
    private readonly ListFilterState filter = new();

    public void Dispose() => planner.Dispose();

    [Fact]
    public void AnAreaInTheSidebarNarrowsEveryListAndTheChoiceStaysAcrossLists()
    {
        Add("Fix the shelf @Home", Today);
        Add("Send the invoice @Work", Today);
        Add("Water plants @Home", Today.AddDays(1));
        var today = List(ListKind.Today);
        var tomorrow = List(ListKind.Tomorrow);
        var filters = Filters();

        filters.SelectedArea = filters.AreaChoices.Single(area => area.Label == "Home");

        Assert.Equal(["Fix the shelf"], Titles(today));
        Assert.Equal(["Water plants"], Titles(tomorrow));
        Assert.True(today.HasFilter);
        Assert.Equal("Lists.Filtered(Home)", today.FilterText);
        Assert.Equal("Home", filters.SelectedArea!.Label);
        Assert.True(filters.IsAreaChosen);
    }

    [Fact]
    public void ATagAndAnAreaTogetherMustBothMatch()
    {
        Add("Buy stamps @Home #errand", Today);
        Add("Pick up parcel @Work #errand", Today);
        Add("Fix the shelf @Home", Today);
        var today = List(ListKind.Today);
        var filters = Filters();

        filters.SelectedTag = filters.TagChoices.Single(tag => tag.Label == "#errand");
        Assert.Equal(["Buy stamps", "Pick up parcel"], Titles(today));

        filters.SelectedArea = filters.AreaChoices.Single(area => area.Label == "Home");
        Assert.Equal(["Buy stamps"], Titles(today));
        Assert.Equal("Lists.Filtered(Home · #errand)", today.FilterText);
    }

    [Fact]
    public void ChoosingAllAreasOrClearingShowsEverything()
    {
        Add("Fix the shelf @Home", Today);
        Add("Send the invoice @Work", Today);
        var today = List(ListKind.Today);
        var filters = Filters();

        filters.SelectedArea = filters.AreaChoices.Single(area => area.Label == "Home");
        filters.SelectedArea = filters.AreaChoices[0];
        Assert.Equal(2, Titles(today).Count);
        Assert.False(filters.IsAreaChosen);

        filters.SelectedArea = filters.AreaChoices.Single(area => area.Label == "Work");
        today.ClearFilterCommand.Execute(null);
        Assert.Equal(2, Titles(today).Count);
        Assert.False(today.HasFilter);
        Assert.False(filters.IsFiltering);
    }

    [Fact]
    public void DeletingTheChosenAreaLetsGoOfTheFilter()
    {
        Add("Fix the shelf @Home", Today);
        Add("Send the invoice @Work", Today);
        var today = List(ListKind.Today);
        var filters = Filters();
        filters.SelectedArea = filters.AreaChoices.Single(area => area.Label == "Home");

        planner.Areas.Delete(planner.Areas.Find("Home")!.Id);

        Assert.True(filter.Current.IsEmpty);
        Assert.Equal(2, Titles(today).Count);
    }

    [Fact]
    public void ArchivingTheChosenAreaLetsGoOfTheFilterAndTakesItOutOfThePicker()
    {
        Add("Fix the shelf @Home", Today);
        Add("Send the invoice @Work", Today);
        var today = List(ListKind.Today);
        var filters = Filters();
        filters.SelectedArea = filters.AreaChoices.Single(area => area.Label == "Home");

        planner.Areas.Archive(planner.Areas.Find("Home")!.Id);

        Assert.True(filter.Current.IsEmpty);
        Assert.Equal(2, Titles(today).Count);
        Assert.Equal(["Lists.AllAreas", "Work"], filters.AreaChoices.Select(area => area.Label));
    }

    [Fact]
    public void ThePickersStayAwayUntilThereIsAnAreaOrTag()
    {
        var filters = Filters();
        Assert.False(filters.HasAny);

        Add("Buy stamps #errand", Today);
        Assert.True(filters.HasAny);
        Assert.False(filters.HasAreas);
    }

    private static List<string> Titles(ListViewModel list) => [.. list.Sections.SelectMany(section => section.Rows).Select(row => row.Title)];

    private ListFiltersViewModel Filters() => new(planner.Areas, planner.Tags, filter, planner.Strings, _ => null, action => action());

    private ListViewModel List(ListKind kind)
    {
        var composer = new ComposerViewModel(
            planner.Tasks, planner.Areas, planner.Tags, planner.Projects, planner.Settings, planner.Strings, planner.Time, _ => null, day => day, action => action());
        return new ListViewModel(
            kind,
            planner.Tasks,
            planner.Areas,
            composer,
            planner.Sync,
            planner.Settings,
            planner.Strings,
            planner.Time,
            _ => null,
            () => true,
            planner.Tick,
            action => action(),
            tags: planner.Tags,
            filter: filter);
    }

    private void Add(string line, DateOnly day)
    {
        var draft = ComposerParser.Parse(line, planner.Time.GetLocalNow().DateTime) with { PlannedDate = day };
        Assert.NotNull(planner.Tasks.Add(draft));
        planner.Time.Advance(TimeSpan.FromSeconds(1));
    }
}
