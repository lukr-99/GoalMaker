using GoalMaker.App.Startup;

namespace GoalMaker.App.Tests;

public sealed class StartupOptionsTests
{
    [Fact]
    public void NoArgumentsShowTheWindow() => Assert.Equal(new StartupOptions(false, null), StartupOptions.Parse([]));

    [Fact]
    public void TrayStartsHidden() => Assert.Equal(new StartupOptions(true, null), StartupOptions.Parse(["--tray"]));

    [Theory]
    [InlineData("today", AppPage.Today)]
    [InlineData("Settings", AppPage.Settings)]
    public void OpenShowsAPage(string page, AppPage expected) =>
        Assert.Equal(new StartupOptions(false, expected), StartupOptions.Parse(["--open", page]));

    [Fact]
    public void AnOpenPageWinsOverTray() =>
        Assert.Equal(new StartupOptions(false, AppPage.Settings), StartupOptions.Parse(["--tray", "--open", "settings"]));

    [Fact]
    public void LinksOpenPages() =>
        Assert.Equal(new StartupOptions(false, AppPage.Today), StartupOptions.Parse(["goalmaker://open/today/"]));

    [Fact]
    public void UnknownValuesAreIgnored() =>
        Assert.Equal(new StartupOptions(true, null), StartupOptions.Parse(["--tray", "--open", "habits", "--mini", "today"]));
}
