using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Tests;

public sealed class PlacesViewModelTests
{
    private readonly TestPlanner.FakeSettings settings = new() { PinnedPlaces = ["today", "tomorrow", "inbox", "projects"] };

    private PlacesViewModel Create() => new(settings, new TestPlanner.FormatStrings());

    [Fact]
    public void ThePinsComeFirstAndEveryOtherPlaceIsUnderAllPlaces()
    {
        var places = Create();

        Assert.Equal(["today", "tomorrow", "inbox", "projects"], places.Pinned.Select(entry => entry.Id));
        Assert.Equal(["calendar", "habits", "goals", "wants", "reviews", "stats", "archive"], places.Others.Select(entry => entry.Id));
        Assert.Equal("Nav.Calendar", places.Others[0].Label);
    }

    [Fact]
    public void PinningThePageOnShowMovesItUpAndIsSaved()
    {
        var places = Create();
        var rebuilt = 0;
        places.PinsChanged += (_, _) => rebuilt++;
        places.Current = "stats";
        Assert.False(places.IsCurrentPinned);
        Assert.Equal("Places.Pin", places.PinLabel);

        places.TogglePinCurrentCommand.Execute(null);

        Assert.True(places.IsCurrentPinned);
        Assert.Equal("Places.Unpin", places.PinLabel);
        Assert.Equal(["today", "tomorrow", "inbox", "projects", "stats"], settings.PinnedPlaces);
        Assert.DoesNotContain(places.Others, entry => entry.Id == "stats");
        Assert.Equal(1, rebuilt);
    }

    [Fact]
    public void TheLastPinStaysAndSettingsCantBePinned()
    {
        settings.PinnedPlaces = ["inbox"];
        var places = Create();

        places.Current = "inbox";
        Assert.False(places.CanPinCurrent);
        Assert.False(places.Toggle("inbox"));

        places.Current = PlacesViewModel.Settings;
        Assert.False(places.CanPinCurrent);
        Assert.False(places.Toggle(PlacesViewModel.Settings));
        Assert.Equal(["inbox"], settings.PinnedPlaces);
    }

    [Fact]
    public void GoToFiltersAsYouTypeMovesWithTheArrowsAndOpensTheChoice()
    {
        var places = Create();
        string? opened = null;
        places.PlaceChosen += (_, place) => opened = place;

        places.OpenPaletteCommand.Execute(null);
        Assert.True(places.IsPaletteOpen);
        Assert.Equal(14, places.Matches.Count);

        places.Query = "nav.s";
        Assert.Equal(["stats", "settings"], places.Matches.Select(entry => entry.Id));

        places.MoveSelection(1);
        places.MoveSelection(1);
        Assert.Equal(1, places.SelectedIndex);

        places.ChooseCommand.Execute(null);
        Assert.Equal("settings", opened);
        Assert.False(places.IsPaletteOpen);
    }

    [Fact]
    public void NothingMatchingChoosesNothing()
    {
        var places = Create();
        string? opened = null;
        places.PlaceChosen += (_, place) => opened = place;
        places.OpenPaletteCommand.Execute(null);

        places.Query = "zzz";
        places.ChooseCommand.Execute(null);

        Assert.Empty(places.Matches);
        Assert.Null(opened);
        Assert.True(places.IsPaletteOpen);
    }
}
