using GoalMaker.Core.Settings;

namespace GoalMaker.Core.Tests;

public sealed class WindowPlacementTests
{
    // Two 2560-wide screens side by side, as on the development PC.
    private const double ScreenLeft = 0;
    private const double ScreenTop = 0;
    private const double ScreenWidth = 5120;
    private const double ScreenHeight = 1440;

    [Theory]
    [InlineData(3290, 336, 1100, 720, true)]
    [InlineData(100, 100, 1100, 720, true)]
    [InlineData(5100, 100, 1100, 720, false)]
    [InlineData(6000, 100, 1100, 720, false)]
    [InlineData(100, -200, 1100, 720, false)]
    [InlineData(100, 1430, 1100, 720, false)]
    [InlineData(100, 100, 20, 720, false)]
    public void RestoresOnlyWhereTheTitleCanStillBeGrabbed(double left, double top, double width, double height, bool expected) =>
        Assert.Equal(
            expected,
            new WindowPlacement(left, top, width, height, false).FitsWithin(ScreenLeft, ScreenTop, ScreenWidth, ScreenHeight));
}
