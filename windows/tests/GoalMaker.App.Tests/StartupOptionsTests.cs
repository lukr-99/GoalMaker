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
    [InlineData("tomorrow", AppPage.Tomorrow)]
    [InlineData("Inbox", AppPage.Inbox)]
    [InlineData("Settings", AppPage.Settings)]
    [InlineData("areas", AppPage.Areas)]
    [InlineData("Archive", AppPage.Archive)]
    public void OpenShowsAPage(string page, AppPage expected) =>
        Assert.Equal(new StartupOptions(false, expected), StartupOptions.Parse(["--open", page]));

    [Fact]
    public void AnOpenPageWinsOverTray() =>
        Assert.Equal(new StartupOptions(false, AppPage.Settings), StartupOptions.Parse(["--tray", "--open", "settings"]));

    [Fact]
    public void LinksOpenPages() =>
        Assert.Equal(new StartupOptions(false, AppPage.Today), StartupOptions.Parse(["goalmaker://open/today/"]));

    [Fact]
    public void NoActivateShowsWithoutFocus() =>
        Assert.Equal(new StartupOptions(false, AppPage.Today, NoActivate: true), StartupOptions.Parse(["--no-activate", "--open", "today"]));

    [Fact]
    public void UnknownValuesAreIgnored() =>
        Assert.Equal(new StartupOptions(true, null), StartupOptions.Parse(["--tray", "--open", "nowhere", "--sideways", "later"]));

    [Theory]
    [InlineData("today", MiniPage.Today)]
    [InlineData("Habits", MiniPage.Habits)]
    public void MiniOpensAMiniWindowAndLeavesTheMainWindowAlone(string page, MiniPage expected) =>
        Assert.Equal(new StartupOptions(true, null, Mini: expected), StartupOptions.Parse(["--mini", page]));

    [Fact]
    public void LinksOpenMiniWindows() =>
        Assert.Equal(new StartupOptions(true, null, Mini: MiniPage.Habits), StartupOptions.Parse(["goalmaker://mini/habits/"]));

    [Fact]
    public void AMiniWindowComesWithAPageWhenOneIsAskedFor() =>
        Assert.Equal(
            new StartupOptions(false, AppPage.Today, Mini: MiniPage.Habits),
            StartupOptions.Parse(["--open", "today", "--mini", "habits"]));

    [Fact]
    public void AnUnknownMiniWindowIsIgnored() =>
        Assert.Equal(new StartupOptions(false, null), StartupOptions.Parse(["--mini", "nowhere"]));
}
