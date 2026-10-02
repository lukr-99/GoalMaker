using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Tests;

/// <summary>Where opening Settings lands, and the way from Areas and tags to the full Areas page.</summary>
public sealed class SettingsSectionsViewModelTests
{
    [Fact]
    public void AWaitingUpdateLandsOnUpdatesAsTheMarkPromised()
    {
        Assert.Equal(SettingsSectionsViewModel.Updates, SettingsSectionsViewModel.Landing(hasProblems: false, hasUpdate: true));
    }

    [Fact]
    public void WithProblemsOrNoUpdateThePageOpensAtTheTop()
    {
        // Problems are the first card, so the page starts there.
        Assert.Null(SettingsSectionsViewModel.Landing(hasProblems: true, hasUpdate: true));
        Assert.Null(SettingsSectionsViewModel.Landing(hasProblems: true, hasUpdate: false));
        Assert.Null(SettingsSectionsViewModel.Landing(hasProblems: false, hasUpdate: false));
    }

    [Fact]
    public void ThePcOnlySectionsSitBetweenClaudeAndYourData()
    {
        var order = SettingsSectionsViewModel.Order.ToList();
        var claude = order.IndexOf(SettingsSectionsViewModel.Claude);

        Assert.Equal(SettingsSectionsViewModel.PcOnly, order.Skip(claude + 1).Take(SettingsSectionsViewModel.PcOnly.Count));
        Assert.Equal(SettingsSectionsViewModel.Data, order[claude + 1 + SettingsSectionsViewModel.PcOnly.Count]);
    }

    [Fact]
    public void TheAreasSectionOpensTheFullAreasPage()
    {
        var opened = 0;

        new SettingsSectionsViewModel(() => opened++).OpenAreasCommand.Execute(null);

        Assert.Equal(1, opened);
    }
}
