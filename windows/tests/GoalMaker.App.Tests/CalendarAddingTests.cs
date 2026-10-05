using GoalMaker.App.ViewModels;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;

namespace GoalMaker.App.Tests;

/// <summary>
/// Adding from the calendar on Windows over a real replica (docs/calendar.md, M10-04): the bottom bar
/// adds a task or an event to the picked day, and on several picked days one event across them or a
/// task on each, starting at the choice used last; undo takes back the whole batch.
/// </summary>
public sealed class CalendarAddingTests : IDisposable
{
    // Friday 18 September 2026, so the month grid runs from Monday 31 August to Sunday 4 October.
    private static readonly DateOnly Today = new(2026, 9, 18);
    private readonly TestPlanner planner = new();

    public void Dispose() => planner.Dispose();

    [Fact]
    public void ATaskOnOneDayIsPlannedForItUnlessTheLineNamesADay()
    {
        var page = Page();
        var day = Today.AddDays(3);
        page.Open(day);
        var bar = page.Bar;

        Assert.True(bar.ShowsSwitch);
        Assert.False(bar.ShowsChoice);
        Assert.True(bar.IsTask);
        Assert.Equal("Composer.Placeholder", bar.Placeholder);
        Assert.Equal($"Calendar.BarDayName({day.ToString("D", System.Globalization.CultureInfo.CurrentCulture)})", bar.DaysName);

        bar.Line = "Pack #trip";
        bar.SendCommand.Execute(null);
        bar.Line = "Call mom tomorrow";
        bar.SendCommand.Execute(null);

        Assert.Equal(string.Empty, bar.Line);
        Assert.Equal(day, planner.Task("Pack").PlannedDate);
        Assert.Equal(Today.AddDays(1), planner.Task("Call mom").PlannedDate);
        Assert.Equal(["Pack"], page.DayEntries.Select(entry => entry.Title));
        Assert.Equal("Calendar.Added(Call mom)", page.UndoText);
    }

    [Fact]
    public void WithNoDayOpenTheBarAddsToToday()
    {
        var page = Page();

        page.Bar.Line = "Water the plants";
        page.Bar.SendCommand.Execute(null);

        Assert.Equal(Today, planner.Task("Water the plants").PlannedDate);
    }

    [Fact]
    public void AnEventOnOneDayIsJustItsTitle()
    {
        var page = Page();
        var day = Today.AddDays(2);
        page.Open(day);
        var bar = page.Bar;

        bar.IsEvent = true;
        Assert.False(bar.IsTask);
        Assert.Equal("Calendar.BarEventPlaceholder", bar.Placeholder);
        Assert.True(bar.HasForm);
        Assert.Equal("Event.Add", bar.ButtonName);
        bar.Line = "Dentist #health tomorrow";
        Assert.Equal("Calendar.BarAddEvent", bar.ButtonName);
        bar.SendCommand.Execute(null);

        var added = Assert.Single(planner.Events.All());
        Assert.Equal(("Dentist #health tomorrow", day, day), (added.Title, added.StartsOn, added.EndsOn));
        Assert.Empty(planner.Tasks.All());
        Assert.Equal(["Dentist #health tomorrow"], page.DayEvents.Select(item => item.Title));

        page.UndoCommand.Execute(null);
        Assert.Empty(planner.Events.All());
    }

    [Fact]
    public void ThePlusOpensTheEventEditorOnThePickedDays()
    {
        var page = Page();
        page.Open(Today);
        page.Pick(Today.AddDays(2));
        page.Bar.IsEvent = true;

        page.Bar.PressCommand.Execute(null);

        var editor = page.Editor!;
        Assert.True(editor.IsOpen);
        Assert.Equal((Today.ToDateTime(TimeOnly.MinValue), Today.AddDays(2).ToDateTime(TimeOnly.MinValue)), (editor.FirstDay, editor.LastDay));
    }

    [Fact]
    public void AnEventAcrossAPickRunsFromTheFirstDayToTheLastGapsIncluded()
    {
        var page = Page();
        page.Open(Today.AddDays(4));
        page.Pick(Today);
        page.Pick(Today.AddDays(2));
        var bar = page.Bar;

        Assert.True(page.IsPicking);
        Assert.True(bar.ShowsChoice);
        Assert.False(bar.ShowsSwitch);
        Assert.True(bar.IsEvent);
        Assert.Equal("Calendar.BarDays(3)", bar.DaysText);
        Assert.Equal(
            [Today, Today.AddDays(2), Today.AddDays(4)],
            page.Cells.Where(cell => cell.IsPicked).Select(cell => cell.Day));
        Assert.Contains("Calendar.CellPicked", page.Cells.Single(cell => cell.Day == Today).AutomationName, StringComparison.Ordinal);
        Assert.DoesNotContain("Calendar.CellPicked", page.Cells.Single(cell => cell.Day == Today.AddDays(1)).AutomationName, StringComparison.Ordinal);

        bar.Line = "Prague";
        var chip = Assert.Single(bar.Chips);
        Assert.StartsWith("Calendar.EventSpan(", chip.Label, StringComparison.Ordinal);
        bar.SendCommand.Execute(null);

        var trip = Assert.Single(planner.Events.All());
        Assert.Equal(("Prague", Today, Today.AddDays(4)), (trip.Title, trip.StartsOn, trip.EndsOn));
        Assert.Equal(PickedDaysAdd.OneEvent, planner.Settings.PickedDaysAdd);
        Assert.True(page.IsPicking);
    }

    [Fact]
    public void APickTooWideForAnEventSaysSoAndAddsNothing()
    {
        var page = Page();
        page.Open(Today);
        page.Pick(Today.AddDays(EventRules.MaxSpan + 1));

        page.Bar.Line = "Year abroad";

        Assert.True(page.Bar.IsEvent);
        Assert.True(Assert.Single(page.Bar.Chips).IsWarning);
        Assert.False(page.Bar.SendCommand.CanExecute(null));
    }

    [Fact]
    public void ATaskOnEachOfAPickIsOneCopyPerDayAndUndoTakesThemAllBack()
    {
        var page = Page();
        page.Open(Today);
        page.PickRun(Today.AddDays(2));
        page.Pick(Today.AddDays(5));
        var bar = page.Bar;
        Assert.Equal("Calendar.BarDays(4)", bar.DaysText);

        bar.IsTask = true;
        Assert.Equal("Calendar.BarAddTasks(4)", bar.SendName);
        bar.Line = "Pack #trip friday";
        bar.SendCommand.Execute(null);

        var copies = planner.Tasks.All().OrderBy(task => task.PlannedDate).ToList();
        Assert.Equal([Today, Today.AddDays(1), Today.AddDays(2), Today.AddDays(5)], copies.Select(task => task.PlannedDate));
        Assert.All(copies, task => Assert.Equal("Pack", task.Title));
        Assert.Equal(PickedDaysAdd.TaskOnEachDay, planner.Settings.PickedDaysAdd);
        Assert.True(page.HasUndo);
        Assert.Equal("Calendar.AddedEach(Pack,4)", page.UndoText);

        page.UndoCommand.Execute(null);
        Assert.Empty(planner.Tasks.All());
        Assert.Empty(planner.Events.All());
    }

    [Fact]
    public void CopiesOnSeveralDaysDropARepeatTheLineNames()
    {
        var page = Page();
        page.Open(Today);
        page.Bar.Line = "Stretch daily";
        Assert.Contains(page.Bar.Chips, chip => chip.Symbol == Wpf.Ui.Controls.SymbolRegular.ArrowRepeatAll24);

        page.PickRun(Today.AddDays(2));
        page.Bar.IsTask = true;
        Assert.DoesNotContain(page.Bar.Chips, chip => chip.Symbol == Wpf.Ui.Controls.SymbolRegular.ArrowRepeatAll24);
        page.Bar.SendCommand.Execute(null);

        var copies = planner.Tasks.All().OrderBy(task => task.PlannedDate).ToList();
        Assert.Equal([Today, Today.AddDays(1), Today.AddDays(2)], copies.Select(task => task.PlannedDate));
        Assert.All(copies, task => Assert.Equal(("Stretch", null, null), (task.Title, task.Recurrence, task.SeriesId)));
    }

    [Fact]
    public void ThePickStartsAtTheChoiceUsedLastOnThisPc()
    {
        planner.Settings.PickedDaysAdd = PickedDaysAdd.TaskOnEachDay;
        var page = Page();
        page.Open(Today);
        Assert.True(page.Bar.IsTask);

        page.Pick(Today.AddDays(1));
        Assert.True(page.Bar.IsTask);

        // Only adding counts as using a choice; flipping it alone is not remembered.
        page.Bar.IsEvent = true;
        Assert.Equal(PickedDaysAdd.TaskOnEachDay, planner.Settings.PickedDaysAdd);
        page.Bar.Line = "Festival";
        page.Bar.SendCommand.Execute(null);
        Assert.Equal(PickedDaysAdd.OneEvent, planner.Settings.PickedDaysAdd);

        // Back to one day the switch is on Task again, and a new pick starts at one event.
        page.Open(Today.AddDays(3));
        Assert.True(page.Bar.IsTask);
        Assert.True(page.Bar.ShowsSwitch);
        page.PickRun(Today.AddDays(4));
        Assert.True(page.Bar.IsEvent);
    }

    [Fact]
    public void TheKeyboardPicksDaysOnACellAndEscGoesBackToOne()
    {
        var page = Page();
        Cell(page, Today).OpenCommand.Execute(null);
        Assert.False(page.LeavePickingCommand.CanExecute(null));

        // Ctrl+Space adds a day, Shift+Space a run from the last one picked, Ctrl+Space again takes one out.
        Cell(page, Today.AddDays(2)).PickCommand.Execute(null);
        Cell(page, Today.AddDays(5)).PickRunCommand.Execute(null);
        Assert.Equal([Today, Today.AddDays(2), Today.AddDays(3), Today.AddDays(4), Today.AddDays(5)], Picked(page));
        Assert.Equal(Today.AddDays(5).ToString("D", System.Globalization.CultureInfo.CurrentCulture), page.DayTitle);
        Cell(page, Today.AddDays(3)).PickCommand.Execute(null);
        Assert.Equal([Today, Today.AddDays(2), Today.AddDays(4), Today.AddDays(5)], Picked(page));
        Assert.Equal("Calendar.BarDays(4)", page.Bar.DaysText);

        // Esc leaves picking with the open day, the last one touched, as the one day.
        Assert.True(page.LeavePickingCommand.CanExecute(null));
        page.LeavePickingCommand.Execute(null);
        Assert.False(page.IsPicking);
        Assert.Empty(Picked(page));
        Assert.Equal([Today.AddDays(5)], page.PickedDays());
        Assert.True(page.Bar.ShowsSwitch);

        // A plain click on one day while picking leaves picking too, and opens that day.
        Cell(page, Today.AddDays(5)).PickRunCommand.Execute(null);
        Cell(page, Today.AddDays(7)).PickRunCommand.Execute(null);
        Assert.Equal(3, Picked(page).Count);
        Cell(page, Today.AddDays(6)).OpenCommand.Execute(null);
        Assert.False(page.IsPicking);
        Assert.Equal([Today.AddDays(6)], page.PickedDays());
        Assert.Equal(Today.AddDays(6).ToString("D", System.Globalization.CultureInfo.CurrentCulture), page.DayTitle);

        // Taking out all but one day leaves one picked, open.
        Cell(page, Today.AddDays(8)).PickCommand.Execute(null);
        Cell(page, Today.AddDays(8)).PickCommand.Execute(null);
        Assert.False(page.IsPicking);
        Assert.Equal([Today.AddDays(6)], page.PickedDays());
    }

    private static CalendarCellViewModel Cell(CalendarViewModel page, DateOnly day) => page.Cells.Single(cell => cell.Day == day);

    private static List<DateOnly> Picked(CalendarViewModel page) => [.. page.Cells.Where(cell => cell.IsPicked).Select(cell => cell.Day)];

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
        action => action(),
        events: planner.Events);
}
