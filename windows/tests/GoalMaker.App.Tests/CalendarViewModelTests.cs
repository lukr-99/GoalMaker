using GoalMaker.App.ViewModels;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>The Windows calendar over a real replica: the grid, the day picked and what it holds (M5-03).</summary>
public sealed class CalendarViewModelTests : IDisposable
{
    // Friday 18 September 2026, so the month grid runs from Monday 31 August to Sunday 4 October.
    private static readonly DateOnly Today = new(2026, 9, 18);
    private readonly TestPlanner planner = new();

    public void Dispose() => planner.Dispose();

    [Fact]
    public void TheMonthIsWholeWeeksAroundToday()
    {
        var page = Page();

        Assert.Equal(35, page.Cells.Count);
        Assert.Equal(new DateOnly(2026, 8, 31), page.Cells[0].Day);
        Assert.Equal(new DateOnly(2026, 10, 4), page.Cells[^1].Day);
        Assert.Contains(page.Cells, cell => cell.IsToday && cell.Day == Today);
        Assert.False(page.HasDay);
        Assert.True(page.HasNoDay);
    }

    [Fact]
    public void TheWeekIsSevenDaysFromItsMonday()
    {
        var page = Page();

        page.ShowCommand.Execute("week");

        Assert.Equal(7, page.Cells.Count);
        Assert.Equal(new DateOnly(2026, 9, 14), page.Cells[0].Day);
        Assert.Equal(new DateOnly(2026, 9, 20), page.Cells[^1].Day);
    }

    [Fact]
    public void BackAndForwardMoveTheMonthAndTodayComesHome()
    {
        var page = Page();

        page.BackCommand.Execute(null);
        Assert.Contains(page.Cells, cell => cell.Day == new DateOnly(2026, 8, 15));

        page.ForwardCommand.Execute(null);
        page.ForwardCommand.Execute(null);
        Assert.Contains(page.Cells, cell => cell.Day == new DateOnly(2026, 10, 15));

        page.Today_Command.Execute(null);
        Assert.Contains(page.Cells, cell => cell.IsToday && cell.Day == Today);
    }

    [Fact]
    public void ADayShowsWhatIsPlannedOnIt()
    {
        Plan("Call the bank 9:00", Today);
        Plan("Water the plants", Today);
        Plan("Not today", Today.AddDays(2));
        var page = Page();

        Assert.Equal(2, page.Cells.Single(cell => cell.Day == Today).Count);

        page.Open(Today);

        Assert.True(page.HasDay);
        Assert.False(page.IsDayEmpty);
        Assert.Equal(["Call the bank", "Water the plants"], page.DayEntries.Select(entry => entry.Title));
        Assert.Equal("Calendar.Planned", page.DayEntries[0].Label);
    }

    [Fact]
    public void ADeadlineAndARepeatShowOnTheirDays()
    {
        var due = Plan("File the receipts", Today);
        planner.Tasks.SetDeadline(due.Id, Today.AddDays(3));
        var repeating = Plan("Water the plants", Today);
        planner.Tasks.SetRecurrence(repeating.Id, "FREQ=DAILY");
        var page = Page();

        page.Open(Today.AddDays(3));
        Assert.Contains(page.DayEntries, entry => entry.Title == "File the receipts" && entry.Label == "Calendar.Deadline");

        page.Open(Today.AddDays(1));
        Assert.Contains(page.DayEntries, entry => entry.Title == "Water the plants" && entry.Label == "Calendar.Repeat");
    }

    [Fact]
    public void PickingTheSameDayAgainClosesIt()
    {
        var page = Page();

        page.Open(Today);
        Assert.True(page.HasDay);

        page.Open(Today);
        Assert.False(page.HasDay);
        Assert.Empty(page.DayEntries);
    }

    private TaskItem Plan(string line, DateOnly day)
    {
        var task = planner.Tasks.Add(ComposerParser.Parse(line, planner.Time.GetLocalNow().DateTime))!;
        planner.Tasks.Plan(task.Id, day);
        return planner.Tasks.Find(task.Id)!;
    }

    private CalendarViewModel Page() => new(
        planner.Tasks,
        planner.Reminders,
        planner.Settings,
        planner.Strings,
        planner.Time,
        _ => { },
        action => action());
}
