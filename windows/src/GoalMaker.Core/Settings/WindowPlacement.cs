namespace GoalMaker.Core.Settings;

/// <summary>Where the main window was last, in device-independent units, and whether it was maximized.</summary>
public sealed record WindowPlacement(double Left, double Top, double Width, double Height, bool Maximized)
{
    private const double MinimumVisible = 64;

    /// <summary>
    /// True when enough of the window's title area lies inside the virtual screen to grab it again,
    /// so a placement saved on a monitor that's gone never restores the window off-screen.
    /// </summary>
    public bool FitsWithin(double screenLeft, double screenTop, double screenWidth, double screenHeight)
    {
        if (Width < MinimumVisible || Height < MinimumVisible)
        {
            return false;
        }

        var visibleWidth = Math.Min(Left + Width, screenLeft + screenWidth) - Math.Max(Left, screenLeft);
        var titleVisible = Top >= screenTop && Top + MinimumVisible / 2 <= screenTop + screenHeight;
        return visibleWidth >= MinimumVisible && titleVisible;
    }
}
