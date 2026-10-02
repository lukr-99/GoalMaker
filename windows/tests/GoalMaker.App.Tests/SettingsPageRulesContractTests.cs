using System.IO;
using System.Text.Json;
using System.Xml.Linq;
using DotNetLib.Tray;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Tests;

/// <summary>
/// contracts/vectors/settings.json, which the phone's SettingsPageRulesContractTest reads too: the
/// section order, the 4+ rule, the current section, the jump's scroll time, the hints, the scroll
/// hint's rule, the Saved mark and the deep link, answered by the settings kit through GoalMaker's
/// <see cref="SettingsPageRules"/>.
/// </summary>
public sealed class SettingsPageRulesContractTests
{
    private static readonly JsonElement Vector = Load();

    [Fact]
    public void TheSectionsFollowTheContractsOrderWithThePcOnlyOnesBeforeYourData()
    {
        var contract = Vector.GetProperty("order").EnumerateArray().Select(id => id.GetString()!).ToList();

        Assert.Equal(contract, SettingsSectionsViewModel.Order.Except(SettingsSectionsViewModel.PcOnly));
        Assert.Equal(SettingsSectionsViewModel.Order, PageSectionIds());
    }

    [Fact]
    public void TheNavigationNumbersAreTheKits()
    {
        var navigation = Vector.GetProperty("navigation");

        Assert.Equal(SettingsLayoutRules.MinimumSectionsForNavigation, navigation.GetProperty("minimumSections").GetInt32());
        Assert.Equal(SettingsLayoutRules.CurrentSectionLine, navigation.GetProperty("currentLine").GetDouble());
        // A page 1100 high in a 1000 high view scrolls 100; within the slack of that counts as the bottom.
        var slack = navigation.GetProperty("bottomSlack").GetDouble();
        Assert.True(SettingsSectionTracker.IsAtBottom(100 - slack, 1000, 1100));
        Assert.False(SettingsSectionTracker.IsAtBottom(100 - slack - 1, 1000, 1100));
    }

    [Fact]
    public void TheNavigationShowsWithFourSectionsOrMore()
    {
        foreach (var check in Vector.GetProperty("showNavigation").EnumerateArray())
        {
            var sections = check.GetProperty("sections").GetArrayLength();
            Assert.True(check.GetProperty("expect").GetBoolean() == SettingsPageRules.ShowsNavigation(sections), Name(check));
        }
    }

    [Fact]
    public void TheCurrentSectionIsTheOneBeingRead()
    {
        foreach (var check in Vector.GetProperty("current").EnumerateArray())
        {
            var sections = check.GetProperty("sections").EnumerateArray().Select(id => id.GetString()!).ToList();
            var tops = check.GetProperty("tops").EnumerateObject().ToDictionary(top => top.Name, top => top.Value.GetDouble());
            var pinned = check.GetProperty("pinned").ValueKind == JsonValueKind.Null ? null : check.GetProperty("pinned").GetString();
            var expected = check.GetProperty("expect").ValueKind == JsonValueKind.Null ? null : check.GetProperty("expect").GetString();

            var current = SettingsPageRules.CurrentAt(
                sections, tops, check.GetProperty("scroll").GetDouble(), check.GetProperty("maxScroll").GetDouble(), pinned);

            Assert.True(expected == current, $"{Name(check)}: expected {expected}, got {current}");
        }
    }

    [Fact]
    public void AJumpScrollsIn250To450Milliseconds()
    {
        foreach (var check in Vector.GetProperty("jumpScroll").EnumerateArray())
        {
            var millis = SettingsPageRules.JumpScrollMilliseconds(check.GetProperty("distance").GetDouble(), check.GetProperty("reduceMotion").GetBoolean());

            Assert.True(check.GetProperty("expect").GetInt32() == millis, $"{Name(check)}: got {millis}");
        }
    }

    [Fact]
    public void TheHintsRiseHoldAndFadeAsTheContractSays()
    {
        foreach (var check in Vector.GetProperty("hints").EnumerateArray())
        {
            var kind = check.GetProperty("kind").GetString() == "jump" ? SettingsHintKind.Jump : SettingsHintKind.Scroll;
            var hint = SettingsPageRules.Hint(kind, check.GetProperty("reduceMotion").GetBoolean());
            var expect = check.GetProperty("expect");
            if (expect.ValueKind == JsonValueKind.Null)
            {
                Assert.True(hint is null, Name(check));
                continue;
            }

            Assert.NotNull(hint);
            Assert.Equal(expect.GetProperty("rise").GetDouble(), hint.Rise.TotalMilliseconds);
            Assert.Equal(expect.GetProperty("hold").GetDouble(), hint.Hold.TotalMilliseconds);
            Assert.Equal(expect.GetProperty("fade").GetDouble(), hint.Fade.TotalMilliseconds);
            Assert.Equal(expect.GetProperty("tint").GetBoolean(), hint.ShowsTint);
            Assert.Equal(expect.GetProperty("ring").GetBoolean(), hint.ShowsRing);
            Assert.Equal(expect.GetProperty("glow").GetBoolean(), hint.ShowsGlow);
            // The kit holds the edge bar at this much at the peak; the contract calls it how far the bar grows.
            Assert.Equal(expect.GetProperty("edge").GetDouble(), hint.EdgePeak);
            // The kit colors the title in every hint it plays.
            Assert.True(expect.GetProperty("title").GetBoolean(), Name(check));
        }
    }

    [Fact]
    public void TheScrollHintPlaysOnceThePageIsStill()
    {
        Assert.Equal(Vector.GetProperty("scrollHintIdle").GetDouble(), SettingsMotion.ScrollIdleDelay.TotalMilliseconds);
        foreach (var check in Vector.GetProperty("scrollHints").EnumerateArray())
        {
            var plays = SettingsPageRules.PlaysScrollHint(
                Text(check, "current"), Text(check, "lastHinted"), check.GetProperty("jumping").GetBoolean(), check.GetProperty("reduceMotion").GetBoolean());

            Assert.True(check.GetProperty("expect").GetBoolean() == plays, Name(check));
        }
    }

    [Fact]
    public void SavedFadesInHoldsAndFadesOut()
    {
        var saved = Vector.GetProperty("saved");

        Assert.Equal(saved.GetProperty("in").GetDouble(), SettingsMotion.SavedIn.TotalMilliseconds);
        Assert.Equal(saved.GetProperty("hold").GetDouble(), SettingsMotion.SavedHold.TotalMilliseconds);
        Assert.Equal(saved.GetProperty("out").GetDouble(), SettingsMotion.SavedOut.TotalMilliseconds);
    }

    [Fact]
    public void TheUpdateMarkLandsOnUpdates()
    {
        var link = Vector.GetProperty("deepLinks").EnumerateArray().Single(check => check.GetProperty("link").GetString() == "update");

        Assert.Equal(link.GetProperty("expect").GetString(), SettingsSectionsViewModel.Landing(hasProblems: false, hasUpdate: true));
    }

    private static string? Text(JsonElement check, string name) =>
        check.GetProperty(name).ValueKind == JsonValueKind.Null ? null : check.GetProperty(name).GetString();

    private static string Name(JsonElement check) => check.GetProperty("name").GetString()!;

    // The SectionId of every card in SettingsPage.xaml, in the order the page shows them.
    private static List<string> PageSectionIds()
    {
        var page = XDocument.Load(Path.Combine(Root(), "windows", "src", "GoalMaker.App", "Views", "SettingsPage.xaml"));
        return [.. page.Descendants().Where(element => element.Name.LocalName == "SettingsSection").Select(element => (string)element.Attribute("SectionId")!)];
    }

    private static JsonElement Load()
    {
        using var document = JsonDocument.Parse(File.ReadAllBytes(Path.Combine(Root(), "contracts", "vectors", "settings.json")));
        return document.RootElement.Clone();
    }

    private static string Root()
    {
        var directory = new DirectoryInfo(AppContext.BaseDirectory);
        while (directory is not null && !Directory.Exists(Path.Combine(directory.FullName, "contracts")))
        {
            directory = directory.Parent;
        }

        return directory?.FullName ?? throw new DirectoryNotFoundException("contracts/ not found above " + AppContext.BaseDirectory);
    }
}
