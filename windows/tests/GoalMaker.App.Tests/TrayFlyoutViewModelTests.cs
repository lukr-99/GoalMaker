using System.Globalization;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>The tray's Today flyout over a real replica (spec, story 79).</summary>
public sealed class TrayFlyoutViewModelTests : IDisposable
{
    private static readonly DateOnly Today = new(2026, 9, 18);
    private readonly TestPlanner planner = new();
    private int opened;
    private int quickAdds;

    public void Dispose() => planner.Dispose();

    [Fact]
    public void ListsTodayOverdueFirstAndLeavesOtherDaysOut()
    {
        Add("File the receipts", Today.AddDays(-1));
        Add("Stretch !", Today);
        Add("Call the bank 17:00", Today);
        Add("Pack gym bag", Today.AddDays(1));
        Add("Read about sourdough", null);

        var flyout = Flyout();

        Assert.Equal(["File the receipts", "Stretch", "Call the bank"], flyout.Rows.Select(row => row.Title));
        Assert.True(flyout.Rows[0].HasDay);
        Assert.False(flyout.IsEmpty);
        Assert.False(flyout.HasMore);
    }

    [Fact]
    public void CheckingATaskFinishesItAndTheFlyoutMovesOn()
    {
        Add("Stretch", Today);
        Add("Buy milk", Today);
        var flyout = Flyout();

        flyout.Rows[0].IsDone = true;

        Assert.Equal(TaskState.Done, planner.Task("Stretch").State);
        Assert.Equal(["Buy milk"], flyout.Rows.Select(row => row.Title));
        Assert.Equal($"Lists.TodaySummary({Today.ToString("dddd d MMMM", CultureInfo.CurrentCulture)},1,2)", flyout.Subtitle);
    }

    [Fact]
    public void ALongDayIsCountedPastTheRowsItShows()
    {
        for (var index = 0; index < TrayFlyoutViewModel.MaxRows + 3; index++)
        {
            Add($"Task {index}", Today);
        }

        var flyout = Flyout();

        Assert.Equal(TrayFlyoutViewModel.MaxRows, flyout.Rows.Count);
        Assert.True(flyout.HasMore);
        Assert.Equal("Tray.More(3)", flyout.MoreText);
    }

    [Fact]
    public void AnEmptyDaySaysSoAndTheButtonsStillWork()
    {
        var flyout = Flyout();
        flyout.QuickAddCommand.Execute(null);
        flyout.OpenAppCommand.Execute(null);

        Assert.True(flyout.IsEmpty);
        Assert.Equal((1, 1), (quickAdds, opened));
    }

    private TrayFlyoutViewModel Flyout() => new(
        planner.Tasks,
        planner.Areas,
        planner.Settings,
        planner.Strings,
        planner.Time,
        _ => null,
        action => action(),
        () => opened++,
        () => quickAdds++);

    private void Add(string line, DateOnly? day)
    {
        var draft = ComposerParser.Parse(line, planner.Time.GetLocalNow().DateTime) with { PlannedDate = day };
        Assert.NotNull(planner.Tasks.Add(draft));
        planner.Time.Advance(TimeSpan.FromSeconds(1));
    }
}
