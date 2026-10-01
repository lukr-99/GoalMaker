using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Tests;

/// <summary>The section list on the left of Settings: its sections, the jumps, and the section in view.</summary>
public sealed class SettingsSectionsViewModelTests
{
    private static readonly string[] Everyday =
    [
        SettingsSectionsViewModel.Account,
        SettingsSectionsViewModel.Appearance,
        SettingsSectionsViewModel.Planning,
        SettingsSectionsViewModel.Areas,
        SettingsSectionsViewModel.Connector,
        SettingsSectionsViewModel.QuickAdd,
        SettingsSectionsViewModel.Backup,
        SettingsSectionsViewModel.Startup,
        SettingsSectionsViewModel.MiniWindows,
        SettingsSectionsViewModel.Updates,
        SettingsSectionsViewModel.About,
    ];

    private readonly List<string> jumps = [];
    private int areasOpened;

    [Fact]
    public void AReleaseBuildListsTheEverydaySectionsInPageOrder()
    {
        var sections = Sections();

        Assert.Equal(Everyday, sections.Items.Select(item => item.Id));
        Assert.Equal("Settings.Account", sections.Items[0].Title);
        Assert.Equal("Settings.JumpTo(Settings.Updates)", sections.Items.Single(item => item.Id == SettingsSectionsViewModel.Updates).AccessibleName);
    }

    [Fact]
    public void AreasAndTagsHaveASectionOfTheirOwnAfterPlanning()
    {
        var ids = SettingsSectionsViewModel.Visible(hasProblems: false, devBuild: false);

        Assert.Equal(SettingsSectionsViewModel.Planning, ids[ids.ToList().IndexOf(SettingsSectionsViewModel.Areas) - 1]);
        Assert.Equal("Areas.Title", Sections().Items.Single(item => item.Id == SettingsSectionsViewModel.Areas).Title);
    }

    [Fact]
    public void ProblemsComeFirstWhileThereAreSomeAndDeveloperLastInADevBuild()
    {
        var sections = Sections(devBuild: true);
        sections.Show(hasProblems: true);

        Assert.Equal([SettingsSectionsViewModel.Problems, .. Everyday, SettingsSectionsViewModel.Developer], sections.Items.Select(item => item.Id));

        sections.Show(hasProblems: false);
        Assert.Equal([.. Everyday, SettingsSectionsViewModel.Developer], sections.Items.Select(item => item.Id));
    }

    [Fact]
    public void AJumpMarksTheSectionAndAsksThePageToScrollThere()
    {
        var sections = Sections();

        sections.Items.Single(item => item.Id == SettingsSectionsViewModel.Updates).JumpCommand.Execute(null);

        Assert.Equal([SettingsSectionsViewModel.Updates], jumps);
        Assert.Equal(SettingsSectionsViewModel.Updates, sections.Current);
        Assert.Equal([SettingsSectionsViewModel.Updates], sections.Items.Where(item => item.IsCurrent).Select(item => item.Id));
    }

    [Fact]
    public void UpdatesCanAlwaysBeReachedAndAnUnknownSectionDoesNothing()
    {
        foreach (var problems in new[] { false, true })
        {
            foreach (var dev in new[] { false, true })
            {
                Assert.Contains(SettingsSectionsViewModel.Updates, SettingsSectionsViewModel.Visible(problems, dev));
            }
        }

        var sections = Sections();
        sections.JumpTo(SettingsSectionsViewModel.Developer);
        Assert.Empty(jumps);
        Assert.Equal(SettingsSectionsViewModel.Account, sections.Current);
    }

    [Fact]
    public void ScrollingMarksTheSectionWhoseTopHasReachedTheTop()
    {
        var sections = Sections();
        var tops = Tops();

        sections.Follow(tops, offset: 0, maxOffset: 4000);
        Assert.Equal(SettingsSectionsViewModel.Account, sections.Current);

        sections.Follow(tops, offset: 1490, maxOffset: 4000);
        Assert.Equal(SettingsSectionsViewModel.Areas, sections.Current);
        Assert.Single(sections.Items, item => item.IsCurrent);
    }

    [Fact]
    public void AtTheBottomTheLastSectionIsCurrentUnlessAJumpLandedOnAnotherInView()
    {
        var tops = Tops();
        var ids = Everyday.ToList();

        Assert.Equal(SettingsSectionsViewModel.About, SettingsSectionsViewModel.CurrentAt(ids, tops, 4500, 4500));
        Assert.Equal(SettingsSectionsViewModel.Updates, SettingsSectionsViewModel.CurrentAt(ids, tops, 4500, 4500, jumped: SettingsSectionsViewModel.Updates));
        Assert.Equal(SettingsSectionsViewModel.About, SettingsSectionsViewModel.CurrentAt(ids, tops, 4500, 4500, jumped: SettingsSectionsViewModel.Account));

        var sections = Sections();
        sections.JumpTo(SettingsSectionsViewModel.Updates);
        sections.Follow(tops, offset: 4500, maxOffset: 4500);
        Assert.Equal(SettingsSectionsViewModel.Updates, sections.Current);
    }

    [Fact]
    public void BeforeAnythingIsMeasuredTheFirstSectionIsCurrent()
    {
        Assert.Equal(SettingsSectionsViewModel.Account, SettingsSectionsViewModel.CurrentAt(Everyday, new Dictionary<string, double>(), 0, 0));
        Assert.Null(SettingsSectionsViewModel.CurrentAt([], new Dictionary<string, double>(), 0, 0));
    }

    [Fact]
    public void TheAreasSectionOpensTheFullAreasPage()
    {
        Sections().OpenAreasCommand.Execute(null);

        Assert.Equal(1, areasOpened);
    }

    // Each section 500 high, one after the other.
    private static Dictionary<string, double> Tops() =>
        Everyday.Select((id, index) => (id, top: index * 500.0)).ToDictionary(pair => pair.id, pair => pair.top);

    private SettingsSectionsViewModel Sections(bool devBuild = false)
    {
        var sections = new SettingsSectionsViewModel(new TestPlanner.FormatStrings(), devBuild, () => areasOpened++);
        sections.JumpRequested += (_, id) => jumps.Add(id);
        return sections;
    }
}
