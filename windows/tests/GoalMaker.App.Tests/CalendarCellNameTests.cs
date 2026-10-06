using System.Globalization;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>
/// A calendar cell as a screen reader says it (M6-05): the date, whether it is today or open, and what
/// is on it, as the phone's cell says it.
/// </summary>
public sealed class CalendarCellNameTests : IDisposable
{
    private static readonly DateOnly Today = new(2026, 9, 18);
    private readonly TestPlanner planner = new();

    public void Dispose() => planner.Dispose();

    [Fact]
    public void ACellSaysItsDateWhetherItIsTodayAndWhatIsOnIt()
    {
        Plan("Call the bank", Today);
        Plan("Stretch", Today);
        var due = Plan("File the receipts", Today.AddDays(-5));
        planner.Tasks.SetDeadline(due.Id, Today);
        var page = Page();

        page.Open(Today);

        var cell = page.Cells.Single(cell => cell.Day == Today);
        Assert.Equal($"{Date(Today)}, Calendar.CellToday, Calendar.CellOpen, Calendar.CellPlanned(2), Calendar.CellDueOne", cell.AutomationName);
    }

    [Fact]
    public void AnEmptyCellSaysSo()
    {
        var cell = Page().Cells.Single(cell => cell.Day == Today.AddDays(2));

        Assert.Equal($"{Date(Today.AddDays(2))}, Calendar.CellEmpty", cell.AutomationName);
    }

    [Fact]
    public void TodayAndTomorrowStandOutAndSaySo()
    {
        var page = Page();
        page.Open(Today.AddDays(1));

        var today = page.Cells.Single(cell => cell.Day == Today);
        var tomorrow = page.Cells.Single(cell => cell.Day == Today.AddDays(1));
        var other = page.Cells.Single(cell => cell.Day == Today.AddDays(2));

        Assert.Equal((true, false, "Calendar.TagToday"), (today.IsToday, today.IsTomorrow, today.DayTag));
        Assert.Equal((false, true, "Calendar.TagTomorrow"), (tomorrow.IsToday, tomorrow.IsTomorrow, tomorrow.DayTag));
        Assert.Equal((false, false, false), (other.IsToday, other.IsTomorrow, other.HasDayTag));
        Assert.StartsWith($"{Date(Today.AddDays(1))}, Calendar.CellTomorrow, Calendar.CellOpen", tomorrow.AutomationName);
        Assert.DoesNotContain("Calendar.CellTomorrow", today.AutomationName);

        page.ShowCommand.Execute("week");
        Assert.True(page.Cells.Single(cell => cell.Day == Today).IsToday);
        Assert.True(page.Cells.Single(cell => cell.Day == Today.AddDays(1)).IsTomorrow);
    }

    private static string Date(DateOnly day) => day.ToString("D", CultureInfo.CurrentCulture);

    private TaskItem Plan(string line, DateOnly day)
    {
        var task = planner.Tasks.Add(ComposerParser.Parse(line, planner.Time.GetLocalNow().DateTime))!;
        planner.Tasks.Plan(task.Id, day);
        return planner.Tasks.Find(task.Id)!;
    }

    private CalendarViewModel Page() => new(
        planner.Tasks,
        planner.Reminders,
        planner.Areas,
        planner.Tags,
        planner.Projects,
        planner.Settings,
        planner.Strings,
        _ => null,
        planner.Time,
        _ => { },
        action => action());
}
