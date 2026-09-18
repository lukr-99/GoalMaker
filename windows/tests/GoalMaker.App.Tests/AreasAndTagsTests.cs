using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>Managing areas and tags on a real replica (M2-11).</summary>
public sealed class AreasAndTagsTests : IDisposable
{
    private readonly TestPlanner planner = new();

    public void Dispose() => planner.Dispose();

    [Fact]
    public void AnAreaIsRenamedRecoloredAndGivenAnEmoji()
    {
        var home = planner.Areas.Create("Home")!;

        Assert.True(planner.Areas.Rename(home.Id, " House "));
        Assert.True(planner.Areas.Recolor(home.Id, "blue"));
        Assert.True(planner.Areas.SetEmoji(home.Id, "🏠"));

        Assert.Equal(new AreaItem(home.Id, "House", "blue", "🏠"), planner.Areas.All().Single());
        Assert.True(planner.Areas.SetEmoji(home.Id, "  "));
        Assert.Null(planner.Areas.All().Single().Emoji);
    }

    [Fact]
    public void AnAreaCantTakeAnotherAreasNameOrAColorOutsideThePalette()
    {
        var home = planner.Areas.Create("Home")!;
        planner.Areas.Create("Work");

        Assert.False(planner.Areas.Rename(home.Id, "work"));
        Assert.False(planner.Areas.Rename(home.Id, "  "));
        Assert.False(planner.Areas.Recolor(home.Id, "chartreuse"));
        Assert.Equal(["Home", "Work"], planner.Areas.All().Select(area => area.Name));
    }

    [Fact]
    public void MovingAnAreaReordersThemAll()
    {
        var home = planner.Areas.Create("Home")!;
        planner.Areas.Create("Work");
        planner.Areas.Create("Health");

        planner.Areas.Move(home.Id, 2);

        Assert.Equal(["Work", "Health", "Home"], planner.Areas.All().Select(area => area.Name));
    }

    [Fact]
    public void DeletingAnAreaKeepsItsTasksWithoutIt()
    {
        Add("Fix the shelf @Home");
        var home = planner.Areas.Find("Home")!;

        planner.Areas.Delete(home.Id);

        Assert.Empty(planner.Areas.All());
        Assert.Null(planner.Task("Fix the shelf").AreaId);
    }

    [Fact]
    public void TagsAreRenamedAndDeletedWithTheirLinksAndTheFilterReadsTheLinks()
    {
        Add("Buy stamps #errand #post");
        var task = planner.Task("Buy stamps");
        var errand = planner.Tags.All().Single(tag => tag.Name == "errand");
        var post = planner.Tags.All().Single(tag => tag.Name == "post");

        Assert.Equal(new[] { errand.Id, post.Id }.Order(StringComparer.Ordinal), planner.Tags.TagLinks()[task.Id].Order(StringComparer.Ordinal));
        Assert.False(planner.Tags.Rename(errand.Id, "POST"));
        Assert.True(planner.Tags.Rename(errand.Id, "chores"));
        planner.Tags.Delete(post.Id);

        Assert.Equal(["chores"], planner.Tags.Names());
        Assert.Equal([errand.Id], planner.Tags.TagLinks()[task.Id]);
        Assert.Equal([task.Id], new ListFilter(TagId: errand.Id).Apply(planner.Tasks.All(), planner.Tags.TagLinks()).Select(item => item.Id));
    }

    private void Add(string line)
    {
        Assert.NotNull(planner.Tasks.Add(ComposerParser.Parse(line, planner.Time.GetLocalNow().DateTime)));
        planner.Time.Advance(TimeSpan.FromSeconds(1));
    }
}
