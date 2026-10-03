using GoalMaker.App.ViewModels;
using GoalMaker.Core.Planning;
using GoalMaker.Infrastructure.Sync;

namespace GoalMaker.App.Tests;

/// <summary>
/// The Tally page over a real replica (M8-13): today's bar and the week's bars from the day totals,
/// the filter chips, time per project, the switch, and the rules and categories panels.
/// </summary>
public sealed class TallyViewModelTests : IDisposable
{
    // Friday 18 September 2026; the week runs from Monday the 14th.
    private static readonly DateOnly Today = new(2026, 9, 18);
    private readonly TestPlanner planner = new();
    private readonly TallyDefaults defaults = ContractResources.TallyDefaults();
    private readonly List<bool> switched = [];

    public void Dispose() => planner.Dispose();

    [Fact]
    public void TodayIsOneBarByCategoryMostFirstAndTheWeekABarPerDay()
    {
        planner.TallyDay(Today, TallyRules.Pc, "coding", 90);
        planner.TallyDay(Today, TallyRules.Phone, "video", 30);
        planner.TallyDay(Today, TallyRules.Pc, "video", 15);
        planner.TallyDay(Today.AddDays(-1), TallyRules.Pc, "coding", 60);
        planner.TallyDay(Today.AddDays(-7), TallyRules.Pc, "coding", 500);

        var page = Page();

        Assert.True(page.HasTime);
        Assert.Equal("Tally.HoursMinutes(2,15)", page.DayTotal);
        Assert.Equal([90d, 45d], page.DayParts.Select(part => part.Amount));
        Assert.Equal(
            [("Coding", "Tally.HoursMinutes(1,30)"), ("Video", "Tally.Minutes(45)")],
            page.DayLegend.Select(segment => (segment.Name, segment.Value)));

        Assert.Equal(7, page.WeekDays.Count);
        Assert.True(page.WeekDays[4].IsCurrent);
        Assert.Equal(1d, page.WeekDays[4].Fraction);
        Assert.Equal(60d / 135d, page.WeekDays[3].Fraction, 9);
        Assert.Equal([60d], page.WeekDays[3].Parts.Select(part => part.Amount));
        Assert.Equal(string.Empty, page.WeekDays[0].Value);
        Assert.Equal("Tally.WeekTotal(Tally.HoursMinutes(3,15))", page.WeekTotal);
    }

    [Fact]
    public void NothingThisWeekSaysSo()
    {
        planner.TallyDay(Today.AddDays(-7), TallyRules.Pc, "coding", 60);

        var page = Page();

        Assert.False(page.HasTime);
        Assert.False(page.HasDay);
        Assert.Equal("Tally.Minutes(0)", page.DayTotal);
        Assert.Equal([TallyRules.Phone, TallyRules.Pc], page.Chips.Select(chip => chip.Id));
    }

    [Fact]
    public void TheChipsNarrowToADeviceOrACategoryAndLetGoWhenChosenAgain()
    {
        planner.TallyDay(Today, TallyRules.Pc, "coding", 90);
        planner.TallyDay(Today, TallyRules.Phone, "video", 30);
        planner.TallyDay(Today, TallyRules.Pc, "video", 15);
        var page = Page();

        Assert.Equal([TallyRules.Phone, TallyRules.Pc, "coding", "video"], page.Chips.Select(chip => chip.Id));
        Assert.Equal(["Tally.Phone", "Tally.Pc", "Coding", "Video"], page.Chips.Select(chip => chip.Label));

        page.Chips[0].Command.Execute(TallyRules.Phone);
        Assert.Equal(TallyRules.Phone, page.Kind);
        Assert.True(page.Chips[0].IsSelected);
        Assert.Equal("Tally.Minutes(30)", page.DayTotal);

        page.Choose(TallyRules.Pc);
        Assert.Equal("Tally.HoursMinutes(1,45)", page.DayTotal);

        page.Choose("video");
        Assert.Equal("Tally.Minutes(15)", page.DayTotal);
        Assert.True(page.Chips.Single(chip => chip.Id == "video").IsSelected);

        page.Choose(TallyRules.Pc);
        page.Choose("video");
        Assert.Null(page.Kind);
        Assert.Null(page.Category);
        Assert.Equal("Tally.HoursMinutes(2,15)", page.DayTotal);
    }

    [Fact]
    public void ProjectsShowTheirTimeAndOneThatIsGoneIsStillCounted()
    {
        var project = planner.Projects.Add(new ProjectDraft("GoalMaker"))!;
        planner.TallyDay(Today, TallyRules.Pc, "coding", 90, project.Id);
        planner.TallyDay(Today.AddDays(-2), TallyRules.Pc, "coding", 30, project.Id);
        planner.TallyDay(Today, TallyRules.Pc, "coding", 30, "a-deleted-project");
        planner.TallyDay(Today, TallyRules.Pc, "video", 40);

        var page = Page();

        Assert.True(page.HasProjects);
        Assert.Equal(
            [("GoalMaker", "Tally.Hours(2)", 1d), ("Tally.GoneProject", "Tally.Minutes(30)", 0.25)],
            page.Projects.Select(row => (row.Name, row.Value, row.Fraction)));

        page.Choose(TallyRules.Phone);
        Assert.False(page.HasProjects);
    }

    [Fact]
    public void ARuleAddedOnThePageSortsTheNextDaysTime()
    {
        var page = Page();
        var sample = new TallySample(TallyRules.Windows, "firefox.exe", "Puzzle - lichess.org - Mozilla Firefox");
        Assert.NotEqual("games", TallyRules.SortSample(sample, planner.Tally.Rules(), defaults.Rules).Category);

        page.AddRuleCommand.Execute(null);
        Assert.True(page.IsEditingRule);
        Assert.Equal("Tally.AddRule", page.RulePanelTitle);
        page.DraftMatch = page.MatchChoices.Single(choice => choice.Id == TallyRules.Title);
        page.DraftPattern = "lichess";
        page.DraftCategory = page.CategoryChoices.Single(choice => choice.Id == "games");
        page.SaveRuleCommand.Execute(null);

        Assert.False(page.IsEditingRule);
        var row = Assert.Single(page.Rules);
        Assert.Equal("lichess", row.Pattern);
        Assert.Equal("Tally.RuleDetail(Tally.MatchTitle,Tally.OnBoth,Games)", row.Detail);
        // The tracker sorts each new window with the rules the replica holds, whichever app added them.
        Assert.Equal(new TallySort("games", null), TallyRules.SortSample(sample, planner.Tally.Rules(), defaults.Rules));
    }

    [Fact]
    public void ATitleRuleForThePhoneIsRefusedAndTheEditKeepsTheRulesTurn()
    {
        planner.Tally.AddRule(new TallyRule(TallyRules.App, "chess.exe", TallyRules.Windows, "games"));
        planner.Tally.AddRule(new TallyRule(TallyRules.App, "code.exe", TallyRules.Windows, "coding"));
        var page = Page();

        page.AddRuleCommand.Execute(null);
        page.DraftMatch = page.MatchChoices.Single(choice => choice.Id == TallyRules.Title);
        page.DraftPlatform = page.PlatformChoices.Single(choice => choice.Id == TallyRules.Android);
        page.DraftPattern = "lichess";
        page.SaveRuleCommand.Execute(null);
        Assert.True(page.RuleRefused);
        Assert.True(page.IsEditingRule);

        page.Rules[0].EditCommand.Execute(null);
        Assert.Equal("Tally.EditRule", page.RulePanelTitle);
        Assert.Equal("chess.exe", page.DraftPattern);
        Assert.False(page.RuleRefused);
        page.DraftPattern = "lichess.exe";
        page.SaveRuleCommand.Execute(null);
        Assert.Equal(["lichess.exe", "code.exe"], page.Rules.Select(row => row.Pattern));

        page.Rules[1].DeleteCommand.Execute(null);
        Assert.Equal(["lichess.exe"], page.Rules.Select(row => row.Pattern));
    }

    [Fact]
    public void ACategoryAddedOnThePageIsAChoiceForRulesAndCanBeChanged()
    {
        var page = Page();
        Assert.False(page.HasCategories);
        Assert.Equal(defaults.Categories.Select(category => category.Id), page.CategoryChoices.Select(choice => choice.Id));

        page.AddCategoryCommand.Execute(null);
        Assert.False(page.SaveCategoryCommand.CanExecute(null));
        page.DraftName = "Chess";
        page.DraftColor = page.Colors.Single(color => color.Id == "blue");
        page.DraftEmoji = "♟";
        page.SaveCategoryCommand.Execute(null);

        Assert.False(page.IsEditingCategory);
        var row = Assert.Single(page.Categories);
        Assert.Equal(("Chess", "♟", "blue"), (row.Name, row.Emoji, row.Category.Color));
        Assert.Equal("Chess", page.CategoryChoices[^1].Label);

        row.EditCommand.Execute(null);
        Assert.Equal("Tally.EditCategory", page.CategoryPanelTitle);
        page.DraftName = "Board games";
        page.SaveCategoryCommand.Execute(null);
        Assert.Equal("Board games", Assert.Single(page.Categories).Name);

        // Time already sorted into a category that goes keeps a name.
        planner.TallyDay(Today, TallyRules.Pc, row.Category.Id, 20);
        page.Categories[0].DeleteCommand.Execute(null);
        Assert.False(page.HasCategories);
        Assert.Equal("Tally.GoneCategory", Assert.Single(page.DayLegend).Name);
    }

    [Fact]
    public void TheSwitchTurnsTallyOnAndOffOnThisPc()
    {
        var page = Page();
        Assert.False(page.TallyOn);

        page.TallyOn = true;
        page.TallyOn = true;
        page.TallyOn = false;

        Assert.False(planner.Settings.TallyOn);
        Assert.Equal([true, false], switched);
    }

    [Fact]
    public void ADayOfTheWeekShowsCloserUpAndPickingItAgainGoesBackToToday()
    {
        planner.TallyDay(Today, TallyRules.Pc, "coding", 90);
        planner.TallyDay(Today.AddDays(-2), TallyRules.Phone, "video", 40);
        var page = Page();

        Assert.True(page.IsToday);
        Assert.Equal("Tally.Today", page.DayTitle);
        Assert.True(page.WeekDays[4].IsSelected);

        page.WeekDays[2].Pick!.Execute(null);
        Assert.True(page.IsNotToday);
        Assert.True(page.WeekDays[2].IsSelected);
        Assert.False(page.WeekDays[4].IsSelected);
        Assert.Equal("Tally.Minutes(40)", page.DayTotal);
        Assert.Equal(["Video"], page.DayLegend.Select(segment => segment.Name));
        Assert.Equal("Tally.NothingThatDay", page.DayEmpty);

        page.WeekDays[2].Pick!.Execute(null);
        Assert.True(page.IsToday);
        Assert.Equal("Tally.HoursMinutes(1,30)", page.DayTotal);

        page.ShowDay(Today.AddDays(-1));
        page.ShowTodayCommand.Execute(null);
        Assert.True(page.IsToday);
    }

    [Fact]
    public void ThisPcsOwnHoursAndAppsShowWhileTallyIsOnHere()
    {
        var page = Page(Local);
        Assert.False(page.ShowLocal);

        page.TallyOn = true;

        Assert.True(page.ShowLocal);
        Assert.Equal(24, page.Hours.Count);
        Assert.Equal(["04:00", "10:00", "16:00", "22:00"], page.HourMarks);
        Assert.Equal(40d / 60d, page.Hours[5].Fraction, 9);
        Assert.Equal(1d, page.Hours[6].Fraction);
        Assert.Equal([30d, 10d], page.Hours[5].Parts.Select(part => part.Amount / 60));
        Assert.Equal("Tally.HoursSpoken(Tally.HourTip(09:00,Tally.Minutes(40)), Tally.HourTip(10:00,Tally.Hours(1)))", page.HoursDescription);
        Assert.Equal(["coding", "video", "social"], page.Apps.Select(group => group.Category));
        var video = page.Apps[1];
        Assert.Equal(("Video", "Tally.Minutes(30)"), (video.Name, video.Value));
        var chrome = Assert.Single(video.Apps);
        Assert.Equal(("chrome.exe", "Tally.Minutes(30)"), (chrome.App, chrome.Value));
        Assert.Equal([("YouTube", "Tally.Minutes(30)")], chrome.Windows.Select(window => (window.Label, window.Value)));
        Assert.Equal("Tally.MakeRuleFor(chrome.exe)", chrome.MakeRuleName);

        page.ShowAppsCommand.Execute("week");
        Assert.True(page.AppsForWeek);
        Assert.Equal(["coding", "video", "games", "social"], page.Apps.Select(group => group.Category));

        // An open category stays open when the page refreshes.
        page.Apps[0].ToggleCommand.Execute(null);
        page.Refresh();
        Assert.True(page.Apps[0].IsExpanded);
        Assert.False(page.Apps[1].IsExpanded);

        page.Choose("video");
        Assert.Equal(["video"], page.Apps.Select(group => group.Category));
        Assert.Equal(30d / 60d, page.Hours[5].Fraction, 9);

        page.Choose("video");
        page.Choose(TallyRules.Phone);
        Assert.False(page.ShowLocal);
        Assert.True(page.ShowLocalElsewhere);
    }

    [Fact]
    public void MakeARuleUnderAnAppOrASiteFillsThePanelInAndSavingCountsAgain()
    {
        var recounts = 0;
        var page = Page(Local, () => recounts++);
        page.TallyOn = true;
        var chrome = page.Apps.Single(group => group.Category == "video").Apps.Single();

        chrome.MakeRuleCommand.Execute(null);

        Assert.True(page.IsEditingRule);
        Assert.Equal("Tally.AddRule", page.RulePanelTitle);
        Assert.Equal((TallyRules.App, "chrome.exe", TallyRules.Windows, "video"), (page.DraftMatch.Id, page.DraftPattern, page.DraftPlatform.Id, page.DraftCategory!.Id));
        page.DraftCategory = page.CategoryChoices.Single(choice => choice.Id == "music");
        page.SaveRuleCommand.Execute(null);

        Assert.False(page.IsEditingRule);
        Assert.Equal(1, recounts);
        var rule = Assert.Single(planner.Tally.Rules());
        Assert.Equal((TallyRules.App, "chrome.exe", TallyRules.Windows, "music"), (rule.Match, rule.Pattern, rule.Platform, rule.Category));

        chrome.Windows[0].MakeRuleCommand.Execute(null);
        Assert.Equal((TallyRules.Title, "YouTube", "video"), (page.DraftMatch.Id, page.DraftPattern, page.DraftCategory!.Id));

        page.Apps.Single(group => group.Category == "coding").Apps.Single().Windows.Single().MakeRuleCommand.Execute(null);
        Assert.Equal((TallyRules.Folder, "GoalMaker"), (page.DraftMatch.Id, page.DraftPattern));

        page.DeleteRule(Assert.Single(page.Rules));
        Assert.Equal(2, recounts);
    }

    // This PC's own log for the page: today's morning, and an evening earlier in the week.
    private static IReadOnlyList<TallyStretch> Local(DateOnly from, DateOnly to) =>
    [
        new(At(Today, 9, 0), At(Today, 9, 30), "chrome.exe", "Lo-fi beats - YouTube - Google Chrome", "video"),
        new(At(Today, 9, 30), At(Today, 9, 40), "chrome.exe", "r/androiddev - Reddit - Google Chrome", "social"),
        new(At(Today, 10, 0), At(Today, 11, 0), "code.exe", "Tally.cs - GoalMaker - Visual Studio Code", "coding"),
        new(At(Today.AddDays(-1), 20, 0), At(Today.AddDays(-1), 20, 15), "steam.exe", null, "games"),
    ];

    private static DateTime At(DateOnly day, int hour, int minute) => day.ToDateTime(new TimeOnly(hour, minute));

    private TallyViewModel Page(Func<DateOnly, DateOnly, IReadOnlyList<TallyStretch>>? stretches = null, Action? recount = null) => new(
        planner.Tally,
        defaults,
        planner.Projects,
        planner.Settings,
        planner.Strings,
        planner.Time,
        _ => null,
        planner.Areas.Palette(),
        action => action(),
        switched.Add,
        stretches,
        recount);
}
