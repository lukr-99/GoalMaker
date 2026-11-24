using GoalMaker.App.Controls;

namespace GoalMaker.App.Tests;

/// <summary>
/// Where the Focus Board puts the keyboard back after a redraw: the same row, the one now in its place,
/// and nothing once the keyboard has left the list.
/// </summary>
public sealed class KeyboardSpotTests
{
    [Fact]
    public void NothingIsPickedBeforeARowHadTheKeyboard()
    {
        var spot = new KeyboardSpot();
        Assert.False(spot.IsSet);
        Assert.Null(spot.Pick(["a", "b"]));
    }

    [Fact]
    public void AMovedRowKeepsTheKeyboardAndItsPart()
    {
        var spot = new KeyboardSpot();
        spot.Remember("b", 1, "Up");
        Assert.Equal(0, spot.Pick(["b", "a", "c"]));
        Assert.Equal("Up", spot.Part);
    }

    [Fact]
    public void AGoneRowHandsTheKeyboardToTheOneInItsPlace()
    {
        var spot = new KeyboardSpot();
        spot.Remember("b", 1, "Done");
        Assert.Equal(1, spot.Pick(["a", "c"]));

        spot.Remember("c", 2, "Done");
        Assert.Equal(1, spot.Pick(["a", "b"]));
        Assert.Null(spot.Pick([]));
    }

    [Fact]
    public void AForgottenSpotPicksNothingSoASyncDoesNotPullTheKeyboardBack()
    {
        var spot = new KeyboardSpot();
        spot.Remember("a", 0, "Off");
        spot.Forget();
        Assert.False(spot.IsSet);
        Assert.Null(spot.Pick(["a", "b"]));
    }
}
