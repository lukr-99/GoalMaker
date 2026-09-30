using System.Text.Json.Nodes;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;
using GoalMaker.Infrastructure.Sync;

namespace GoalMaker.App.Tests;

/// <summary>The Windows stats page over a real replica: the charts, the heroes and the empty state (M4-07).</summary>
public sealed class StatsViewModelTests : IDisposable
{
    // Friday 18 September 2026; the week on the right of the chart starts on Monday the 14th.
    private static readonly DateOnly Today = new(2026, 9, 18);
    private static readonly DateOnly WeekStart = new(2026, 9, 14);
    private readonly TestPlanner planner = new();

    public void Dispose() => planner.Dispose();

    [Fact]
    public void WithNothingToShowThePageSaysSo()
    {
        var page = Page();

        Assert.True(page.IsEmpty);
        Assert.Equal(StatsRules.Weeks, page.Weeks.Count);
        Assert.Equal("0", page.DoneValue);
    }

    [Fact]
    public void TheWeeksChartCountsFinishedTasksAndNamesTheFullestWeek()
    {
        Done("Monday thing", WeekStart);
        Done("Also Monday", WeekStart);
        Done("Last week", WeekStart.AddDays(-5));
        var page = Page();

        Assert.False(page.IsEmpty);
        Assert.Equal("3", page.DoneValue);
        Assert.Equal(1d, page.Weeks[^1].Fraction);
        Assert.Equal("2", page.Weeks[^1].Value);
        Assert.Equal("1", page.Weeks[^2].Value);
        Assert.Equal(string.Empty, page.Weeks[0].Value);
    }

    [Fact]
    public void TheMonthsChartCountsTheGoalsThatWereHit()
    {
        var hit = planner.Goals.Add(new GoalDraft("Ship the beta", GoalHorizon.Month, new DateOnly(2026, 9, 1)))!;
        planner.Goals.SetStatus(hit.Id, GoalRules.Done);
        planner.Goals.Add(new GoalDraft("Clear the loft", GoalHorizon.Month, new DateOnly(2026, 9, 1)));
        planner.Goals.Add(new GoalDraft("This week", GoalHorizon.Week, WeekStart));
        var page = Page();

        Assert.True(page.HasMonths);
        Assert.Equal(StatsRules.Months, page.Months.Count);
        Assert.Equal("Stats.GoalsValue(1,2)", page.GoalsValue);
        Assert.Equal("Stats.MonthHit(1,2)", page.Months[^1].Value);
        Assert.Equal(0.5, page.Months[^1].Fraction);
    }

    [Fact]
    public void AHabitShowsWhatItHeldAndTheRunItIsOn()
    {
        var habit = planner.Habits.Add(new HabitDraft("Read", Today.AddDays(-6)) { Emoji = "📖" })!;
        for (var back = 0; back < 4; back++)
        {
            planner.Habits.CheckIn(habit.Id, Today.AddDays(-back));
        }

        var page = Page();

        Assert.True(page.HasHabits);
        var row = Assert.Single(page.HabitRows);
        Assert.Equal("Read", row.Name);
        Assert.Equal("Stats.HabitMet(4,7,4)", row.Met);
        Assert.Equal("4", row.Streak);
        Assert.Equal("57%", row.Rate);
    }

    [Fact]
    public void PastRatingsFeedTheMoodAndEnergyChart()
    {
        var first = planner.Reviews.Open(ReviewRules.Weekly, WeekStart.AddDays(-7))!;
        planner.Reviews.SetMood(first.Id, 3);
        planner.Reviews.SetEnergy(first.Id, 4);
        var second = planner.Reviews.Open(ReviewRules.Weekly, WeekStart)!;
        planner.Reviews.SetMood(second.Id, 5);
        var page = Page();

        Assert.True(page.HasRatings);
        Assert.Equal([WeekStart.AddDays(-7), WeekStart], page.Ratings.Select(rating => rating.PeriodStart));
        Assert.Equal([3, 5], page.Ratings.Select(rating => rating.Mood));
        Assert.Null(page.Ratings[1].Energy);
    }

    [Fact]
    public void ANewTaskRefreshesThePage()
    {
        var page = Page();
        Assert.Equal("0", page.DoneValue);

        Done("Something", Today);

        Assert.Equal("1", page.DoneValue);
    }

    [Fact]
    public void TheTallyBlockShowsTwelveWeeksStackedByCategoryOnceThereIsTime()
    {
        Assert.False(Page().HasTally);

        planner.TallyDay(Today, TallyRules.Pc, "coding", 120);
        planner.TallyDay(Today, TallyRules.Phone, "video", 60);
        planner.TallyDay(WeekStart.AddDays(-14), TallyRules.Phone, "video", 90);
        planner.TallyDay(WeekStart.AddDays(-7 * 12), TallyRules.Pc, "coding", 600);
        var page = Page();

        Assert.True(page.HasTally);
        Assert.Equal(TallyRules.WeekCount, page.TallyWeeks.Count);
        Assert.True(page.TallyWeeks[^1].IsCurrent);
        Assert.Equal(1d, page.TallyWeeks[^1].Fraction);
        Assert.Equal([120d, 60d], page.TallyWeeks[^1].Parts.Select(part => part.Amount));
        Assert.Equal(0.5, page.TallyWeeks[^3].Fraction);
        Assert.Equal(string.Empty, page.TallyWeeks[^2].Value);
        Assert.Equal("Tally.HoursMinutes(4,30)", page.TallyTotal);
        Assert.Equal(
            [("Video", "Tally.HoursMinutes(2,30)"), ("Coding", "Tally.Hours(2)")],
            page.TallyLegend.Select(segment => (segment.Name, segment.Value)));
    }

    private StatsViewModel Page()
    {
        planner.Time.SetUtcNow(new DateTimeOffset(2026, 9, 18, 14, 0, 0, TimeSpan.Zero));
        var defaults = ContractResources.TallyDefaults();
        return new StatsViewModel(
            planner.Tasks,
            planner.Goals,
            planner.Habits,
            planner.Reviews,
            planner.Settings,
            planner.Strings,
            planner.Time,
            action => action(),
            planner.Wants,
            planner.Tally,
            own => new TallyLabels(defaults, own, planner.Strings, _ => null));
    }

    private TaskItem Add(string title) => planner.Tasks.Add(ComposerParser.Parse(title, planner.Time.GetLocalNow().DateTime))!;

    // A task finished on a given day: the server stamps completed_at, so the test writes it too.
    private void Done(string title, DateOnly day)
    {
        var task = Add(title);
        planner.Tasks.SetDone(task.Id, true);
        if (planner.Replica.Get("tasks", task.Id) is { } row)
        {
            row["completed_at"] = JsonValue.Create($"{day:yyyy-MM-dd}T18:00:00.000000Z");
            planner.Replica.Queue("tasks", row);
        }
    }
}
