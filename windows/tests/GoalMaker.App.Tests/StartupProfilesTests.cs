using GoalMaker.Core.Startup;
using GoalMaker.Infrastructure.Startup;

namespace GoalMaker.App.Tests;

/// <summary>
/// Handing GoalMaker to Startup Profiles (M5-07, story 84): finding that app, building the request it
/// documents, and staying out of the way when it is not installed. Nothing here writes anything of
/// theirs: the link opens their own confirmation window.
/// </summary>
public sealed class StartupProfilesTests
{
    private const string Installed = @"C:\Users\owner\AppData\Local\Programs\StartupProfiles\StartupProfiles.exe";

    private static readonly StartupProfilesRequest Request = new("com.goalmaker.app", "GoalMaker", @"C:\Apps\GoalMaker.exe")
    {
        Arguments = "--tray",
        Publisher = "Lukáš Krejčí",
        SupportsMinimized = true,
    };

    [Fact]
    public void TheLinkCarriesWhatTheContractAsksFor()
    {
        var link = StartupProfilesLink.Register(Request);

        Assert.Equal(
            "startupprofiles://register?appId=com.goalmaker.app&name=GoalMaker&target=C%3A%5CApps%5CGoalMaker.exe" +
            "&args=--tray&publisher=Luk%C3%A1%C5%A1%20Krej%C4%8D%C3%AD&supportsMinimized=true",
            link);
    }

    [Fact]
    public void TheLinkLeavesOutWhatWasNotGiven()
    {
        var link = StartupProfilesLink.Register(new StartupProfilesRequest("com.goalmaker.app", "GoalMaker", @"C:\GoalMaker.exe"));

        Assert.Equal("startupprofiles://register?appId=com.goalmaker.app&name=GoalMaker&target=C%3A%5CGoalMaker.exe", link);
        Assert.DoesNotContain("suggestedProfile", link, StringComparison.Ordinal);
        Assert.DoesNotContain("supportsMinimized", link, StringComparison.Ordinal);
    }

    [Theory]
    [InlineData("\"C:\\Programs\\StartupProfiles.exe\" \"%1\"", @"C:\Programs\StartupProfiles.exe")]
    [InlineData("C:\\Programs\\StartupProfiles.exe %1", @"C:\Programs\StartupProfiles.exe")]
    [InlineData("  \"C:\\Programs\\StartupProfiles.exe\"  ", @"C:\Programs\StartupProfiles.exe")]
    [InlineData("", null)]
    [InlineData(null, null)]
    [InlineData("\"", null)]
    public void TheRegisteredHandlerNamesTheApp(string? command, string? expected) =>
        Assert.Equal(expected, WindowsStartupProfiles.ExecutableIn(command));

    [Fact]
    public void TheAppIsFoundByItsRegisteredHandler()
    {
        var registered = @"D:\Tools\StartupProfiles\StartupProfiles.exe";
        var profiles = new WindowsStartupProfiles(() => $"\"{registered}\" \"%1\"", path => path == registered, (_, _) => true);

        Assert.Equal(registered, profiles.Find());
    }

    [Fact]
    public void AHandlerPointingAtNothingFallsBackToWhereTheInstallerPutsIt()
    {
        var profiles = new WindowsStartupProfiles(() => "\"D:\\Gone\\StartupProfiles.exe\" \"%1\"", path => path.EndsWith(@"Programs\StartupProfiles\StartupProfiles.exe", StringComparison.Ordinal), (_, _) => true);

        Assert.EndsWith(@"Programs\StartupProfiles\StartupProfiles.exe", profiles.Find(), StringComparison.Ordinal);
    }

    [Fact]
    public void WithoutStartupProfilesThereIsNothingToFindAndNothingIsStarted()
    {
        var started = 0;
        var profiles = new WindowsStartupProfiles(() => null, _ => false, (_, _) => { started++; return true; });

        Assert.Null(profiles.Find());
        Assert.False(profiles.Ask(Request));
        Assert.Equal(0, started);
    }

    [Fact]
    public void AskingStartsThatAppWithTheLinkAndNothingElse()
    {
        var calls = new List<(string App, string Link)>();
        var profiles = new WindowsStartupProfiles(
            () => $"\"{Installed}\" \"%1\"", path => path == Installed, (app, link) => { calls.Add((app, link)); return true; });

        Assert.True(profiles.Ask(Request));

        var (app, link) = Assert.Single(calls);
        Assert.Equal(Installed, app);
        Assert.Equal(StartupProfilesLink.Register(Request), link);
    }

    [Fact]
    public void AnAppThatCannotBeStartedIsReportedAsSuch()
    {
        var profiles = new WindowsStartupProfiles(() => $"\"{Installed}\" \"%1\"", path => path == Installed, (_, _) => false);

        Assert.False(profiles.Ask(Request));
    }
}
