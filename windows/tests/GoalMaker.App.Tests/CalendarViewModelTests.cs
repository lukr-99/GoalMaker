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
    public void ATaskDraggedOntoAnotherDayIsPlannedForIt()
    {
        Plan("Call the bank", Today);
        var page = Page();
        page.Open(Today);
        var card = Assert.Single(page.DayEntries);

        page.MoveTo(card.Id, Today.AddDays(2));

        Assert.Equal(Today.AddDays(2), planner.Task("Call the bank").PlannedDate);
        Assert.Equal(["Call the bank"], page.DayEntries.Select(entry => entry.Title));
        Assert.Contains("20", page.DayTitle);
    }

    [Fact]
    public void AProjectItemOnTheDayWearsItsProjectsChip()
    {
        Plan("Fix the build +GoalMaker", Today);
        Plan("Call the bank", Today);
        var opened = new List<string>();
        var page = Page(opened.Add);

        page.Open(Today);

        var item = page.DayEntries.Single(entry => entry.Title == "Fix the build");
        Assert.True(item.HasProject);
        Assert.Equal("GoalMaker", item.Project!.Name);
        Assert.False(page.DayEntries.Single(entry => entry.Title == "Call the bank").HasProject);
        item.Project.OpenCommand.Execute(null);
        Assert.Equal([planner.Projects.Find("GoalMaker")!.Id], opened);
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

    [Fact]
    public void AnAreaNarrowsTheGridAndTheDayAndAProjectItemTakesItsProjectsArea()
    {
        Plan("Fix the shelf @Home", Today);
        Plan("Send the invoice @Work", Today);
        var item = Plan("Ship the board", Today);
        var project = planner.Projects.Add(new ProjectDraft("GoalMaker") { AreaId = planner.Areas.Find("Work")!.Id })!;
        planner.Tasks.SetProject(item.Id, project.Id, ProjectRules.Task);
        var page = Page();
        Assert.Equal(3, page.Cells.Single(cell => cell.Day == Today).Count);

        page.Filters.SelectedArea = page.Filters.AreaChoices.Single(area => area.Label == "Work");
        page.Open(Today);

        Assert.Equal(2, page.Cells.Single(cell => cell.Day == Today).Count);
        Assert.Equal(["Send the invoice", "Ship the board"], page.DayEntries.Select(entry => entry.Title).Order());
    }

    [Fact]
    public void ATagNarrowsTheDeadlinesAndShowEverythingLetsGo()
    {
        var stamps = Plan("Buy stamps #errand", Today);
        var report = Plan("Write the report", Today);
        planner.Tasks.SetDeadline(stamps.Id, Today.AddDays(2));
        planner.Tasks.SetDeadline(report.Id, Today.AddDays(2));
        var page = Page();

        page.Filters.SelectedTag = page.Filters.TagChoices.Single(tag => tag.Label == "#errand");
        page.Open(Today.AddDays(2));
        Assert.Equal(["Buy stamps"], page.DayEntries.Select(entry => entry.Title));

        page.Filters.ClearCommand.Execute(null);
        Assert.Equal(["Buy stamps", "Write the report"], page.DayEntries.Select(entry => entry.Title).Order());
    }

    private TaskItem Plan(string line, DateOnly day)
    {
        var task = planner.Tasks.Add(ComposerParser.Parse(line, planner.Time.GetLocalNow().DateTime))!;
        planner.Tasks.Plan(task.Id, day);
        return planner.Tasks.Find(task.Id)!;
    }

    private CalendarViewModel Page(Action<string>? openProject = null) => new(
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
        action => action(),
        openProject);
}
