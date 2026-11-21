using GoalMaker.App.ViewModels;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>The Windows areas and tags manager over a real replica (M2-11).</summary>
public sealed class AreasViewModelTests : IDisposable
{
    private readonly TestPlanner planner = new();

    public void Dispose() => planner.Dispose();

    [Fact]
    public void AnAreaIsAddedOnceAndATakenNameIsExplained()
    {
        var manager = Manager();
        manager.NewAreaName = "Home";
        manager.AddAreaCommand.Execute(null);
        manager.NewAreaName = "home";
        manager.AddAreaCommand.Execute(null);

        Assert.Equal(["Home"], manager.Areas.Select(row => row.Name));
        Assert.Equal("home", manager.NewAreaName);
        Assert.Equal("Areas.NameTaken", manager.Status);
    }

    [Fact]
    public void EditingARowSavesTheNameEmojiAndColor()
    {
        planner.Areas.Create("Home");
        var manager = Manager();
        var row = manager.Areas.Single();

        row.Name = "House";
        row.Emoji = "🏠";
        row.Color = manager.Colors.Single(color => color.Id == "blue");

        Assert.Equal(new GoalMaker.Core.Planning.AreaItem(row.Id, "House", "blue", "🏠"), planner.Areas.All().Single());
        Assert.Same(row, manager.Areas.Single());
        Assert.False(manager.HasStatus);
    }

    [Fact]
    public void ARefusedRenameKeepsTheOldName()
    {
        planner.Areas.Create("Home");
        planner.Areas.Create("Work");
        var manager = Manager();
        var home = manager.Areas[0];

        home.Name = "WORK";

        Assert.Equal("Home", home.Name);
        Assert.Equal("Areas.NameTaken", manager.Status);
    }

    [Fact]
    public void RowsMoveAndOnlyTheEndsStopTheArrows()
    {
        planner.Areas.Create("Home");
        planner.Areas.Create("Work");
        planner.Areas.Create("Health");
        var manager = Manager();

        manager.Areas[0].MoveDownCommand.Execute(null);

        Assert.Equal(["Work", "Home", "Health"], manager.Areas.Select(row => row.Name));
        Assert.False(manager.Areas[0].CanMoveUp);
        Assert.True(manager.Areas[1].CanMoveUp && manager.Areas[1].CanMoveDown);
        Assert.False(manager.Areas[2].CanMoveDown);
    }

    [Fact]
    public void TagsAreAddedRenamedAndDeleted()
    {
        var manager = Manager();
        Assert.True(manager.HasNoTags);

        manager.NewTagName = "#errand";
        manager.AddTagCommand.Execute(null);
        manager.Tags.Single().Name = "chores";
        Assert.Equal(["chores"], planner.Tags.Names());

        manager.Tags.Single().DeleteCommand.Execute(null);
        Assert.Empty(planner.Tags.Names());
        Assert.True(manager.HasNoTags);
    }

    [Fact]
    public void ArchivingMovesARowUnderArchivedAndRestoreBringsItBack()
    {
        planner.Areas.Create("Home");
        planner.Areas.Create("Work");
        var manager = Manager();

        manager.Areas[0].ArchiveCommand.Execute(null);

        Assert.Equal(["Work"], manager.Areas.Select(row => row.Name));
        Assert.Equal(["Home"], manager.ArchivedAreas.Select(row => row.Label));
        Assert.True(manager.HasArchived);
        Assert.False(manager.Areas[0].CanMoveUp || manager.Areas[0].CanMoveDown);

        manager.ArchivedAreas.Single().RestoreCommand.Execute(null);

        Assert.Equal(["Home", "Work"], manager.Areas.Select(row => row.Name));
        Assert.False(manager.HasArchived);
    }

    private AreasViewModel Manager() => new(planner.Areas, planner.Tags, planner.Strings, _ => null, action => action());

    [Fact]
    public void ATagCanBelongToAnAreaOrAProjectAndTheListKeepsTheirGroups()
    {
        var school = planner.Areas.Create("School")!;
        var thesis = planner.Projects.Add(new ProjectDraft("Thesis"))!;
        var manager = new AreasViewModel(planner.Areas, planner.Tags, planner.Strings, _ => null, action => action(), planner.Projects);
        manager.NewTagName = "chapter";
        manager.AddTagCommand.Execute(null);
        manager.NewTagName = "forms";
        manager.AddTagCommand.Execute(null);
        manager.NewTagName = "urgent";
        manager.AddTagCommand.Execute(null);

        var chapter = manager.Tags.Single(row => row.Name == "chapter");
        Assert.Equal(["Areas.TagEverywhere", "School", "Areas.TagProject(Thesis)"], chapter.Places.Select(option => option.Label));
        chapter.SelectedPlace = chapter.Places.Single(option => option.Place == TagPlace.Project(thesis.Id));
        var forms = manager.Tags.Single(row => row.Name == "forms");
        forms.SelectedPlace = forms.Places.Single(option => option.Place == TagPlace.Area(school.Id));

        Assert.Equal(thesis.Id, planner.Tags.All().Single(tag => tag.Name == "chapter").ProjectId);
        Assert.Equal(school.Id, planner.Tags.All().Single(tag => tag.Name == "forms").AreaId);
        Assert.Equal(["urgent", "forms", "chapter"], manager.Tags.Select(row => row.Name));
        Assert.Equal(TagPlace.Area(school.Id), manager.Tags.Single(row => row.Name == "forms").SelectedPlace!.Place);
    }
}
