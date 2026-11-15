using System.IO;
using System.Xml.Linq;
using GoalMaker.App.Localization;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.About;
using GoalMaker.Core.Backend;

namespace GoalMaker.App.Tests;

/// <summary>The About page's facts, its three links and its credits (docs/about.md).</summary>
public sealed class AboutViewModelTests
{
    private static readonly BackendEnvironment Backend = new("https://example.supabase.co", "key");

    [Fact]
    public void EachLinkOpensItsAddressInTheBrowser()
    {
        var opened = new List<string>();
        var about = new AboutViewModel(new AppInfo("1.16.0", false, Backend, Backend), new KeyStrings(), opened.Add);

        about.OpenInstagramCommand.Execute(null);
        about.OpenGitHubCommand.Execute(null);
        about.OpenIssuesCommand.Execute(null);

        Assert.Equal(AuthorLinks.All.Select(link => link.Url), opened);
    }

    [Fact]
    public void TheFactsComeFromTheBuild()
    {
        var release = new AboutViewModel(new AppInfo("1.16.0", false, Backend, Backend), new KeyStrings(), _ => { });
        var local = new AboutViewModel(new AppInfo("1.17.0-dev", true, Backend, Backend, LocalOnly: true), new KeyStrings(), _ => { });

        Assert.Equal("1.16.0", release.Version);
        Assert.Equal("About.Release", release.Kind);
        Assert.Equal(Backend.Url, release.Backend);
        Assert.True(release.Syncs);
        Assert.Equal("About.DevBuild", local.Kind);
        Assert.True(local.IsLocalOnly);
        Assert.False(local.Syncs);
        Assert.Equal("LK", release.AuthorMonogram);
        Assert.Equal("@lukas9krejci", release.InstagramHandle);
    }

    [Fact]
    public void BackAsksForSettings()
    {
        var about = new AboutViewModel(new AppInfo("1.16.0", false, Backend, Backend), new KeyStrings(), _ => { });
        var asked = 0;
        about.BackRequested += (_, _) => asked++;

        about.BackCommand.Execute(null);

        Assert.Equal(1, asked);
    }

    [Fact]
    public void EveryFontInTheRepositoryIsCreditedAndNothingIsBlankOrTwice()
    {
        var about = new AboutViewModel(new AppInfo("1.16.0", false, Backend, Backend), new KeyStrings(), _ => { });
        var credits = about.CreditGroups.SelectMany(group => group.Credits).ToList();

        Assert.All(about.CreditGroups, group => Assert.NotEmpty(group.Credits));
        Assert.All(credits, credit =>
        {
            Assert.False(string.IsNullOrWhiteSpace(credit.Name));
            Assert.False(string.IsNullOrWhiteSpace(credit.Role));
            Assert.False(string.IsNullOrWhiteSpace(credit.Licence));
        });
        Assert.Equal(credits.Select(credit => credit.Name).Distinct(), credits.Select(credit => credit.Name));
        var fonts = Directory.GetDirectories(Path.Combine(RepositoryRoot(), "fonts")).Select(folder => Path.GetFileName(folder)!).ToHashSet();
        var credited = credits.Where(credit => credit.Licence == AboutViewModel.Ofl)
            .Select(credit => credit.Name.Replace(" ", "", StringComparison.Ordinal).ToLowerInvariant()).ToHashSet();
        Assert.Equal(fonts, credited);
    }

    [Fact]
    public void EveryStringThePageAndItsViewModelUseIsInTheDictionary()
    {
        var root = RepositoryRoot();
        var app = Path.Combine(root, "windows", "src", "GoalMaker.App");
        XNamespace x = "http://schemas.microsoft.com/winfx/2006/xaml";
        var keys = XDocument.Load(Path.Combine(app, "Resources", "Strings.xaml")).Root!.Elements()
            .Select(element => (string?)element.Attribute(x + "Key")).ToHashSet();
        var used = new KeyStrings();
        _ = new AboutViewModel(new AppInfo("1.16.0", true, Backend, Backend), used, _ => { });
        var page = File.ReadAllText(Path.Combine(app, "Views", "AboutPage.xaml"));
        var inPage = System.Text.RegularExpressions.Regex.Matches(page, "DynamicResource ((About|Settings|App)\\.[A-Za-z.]+)").Select(match => match.Groups[1].Value);

        Assert.All(used.Asked.Concat(inPage), key => Assert.Contains(key, keys));
    }

    private static string RepositoryRoot()
    {
        var directory = new DirectoryInfo(AppContext.BaseDirectory);
        while (directory is not null && !Directory.Exists(Path.Combine(directory.FullName, "contracts")))
        {
            directory = directory.Parent;
        }

        return directory?.FullName ?? throw new DirectoryNotFoundException("contracts/ not found above " + AppContext.BaseDirectory);
    }

    // Answers with the key itself and remembers what was asked.
    private sealed class KeyStrings : IStrings
    {
        public List<string> Asked { get; } = [];

        public string Get(string key, params object[] arguments)
        {
            Asked.Add(key);
            return key;
        }
    }
}
