using System.Windows;
using GoalMaker.App.Shell;

namespace GoalMaker.App.Tests;

/// <summary>
/// How the main window goes up. WPF throws "Cannot show Window when ShowActivated is false and
/// WindowState is set to Maximized", which crashed GoalMaker at start-up whenever a window left
/// maximized was asked for with --no-activate.
/// </summary>
public sealed class WindowShowTests
{
    [Fact]
    public void AMaximizedWindowGoesUpNormalWhenItMustNotTakeTheFocus() =>
        Assert.Equal((WindowState.Normal, WindowState.Maximized), WindowShow.Plan(activate: false, WindowState.Maximized));

    [Fact]
    public void AMaximizedWindowStaysMaximizedWhenItMay() =>
        Assert.Equal((WindowState.Maximized, WindowState.Maximized), WindowShow.Plan(activate: true, WindowState.Maximized));

    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public void AMinimizedWindowComesBackNormal(bool activate) =>
        Assert.Equal((WindowState.Normal, WindowState.Normal), WindowShow.Plan(activate, WindowState.Minimized));

    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public void ANormalWindowIsLeftAlone(bool activate) =>
        Assert.Equal((WindowState.Normal, WindowState.Normal), WindowShow.Plan(activate, WindowState.Normal));
}
