using System.Globalization;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>
/// Calendar events on Windows over a real replica (docs/calendar.md, M10-03): the bars across the grid,
/// the open day's events, the editor with its undo, and Today's line.
/// </summary>
public sealed class CalendarEventsTests : IDisposable
{
    // Friday 18 September 2026, so the month grid runs from Monday 31 August to Sunday 4 October.
    private static readonly DateOnly Today = new(2026, 9, 18);
    private readonly TestPlanner planner = new();

    public void Dispose() => planner.Dispose();

    [Fact]
    public void AnEventAcrossAWeekBreakIsOneBarPerRowOnTheSameLane()
    {
        Add("Early", Today.AddDays(-4), Today.AddDays(-3));
        var trip = Add("Trip", Today, Today.AddDays(4));
        var page = Page();

        Assert.Equal(5, page.Weeks.Count);
        Assert.Equal(page.Cells.Skip(14).Take(7), page.Weeks[2].Cells);
        var first = page.Weeks[2].Bars.Single(bar => bar.Id == trip.Id);
        var second = page.Weeks[3].Bars.Single();
        Assert.Equal((4, 3, 0, false, true), (first.Column, first.Span, first.Lane, first.Before, first.After));
        Assert.Equal((0, 2, 0, true, false), (second.Column, second.Span, second.Lane, second.Before, second.After));
        Assert.Equal("Trip", second.Title);
        Assert.StartsWith("Calendar.EventName(Trip,Calendar.EventSpan(", first.AutomationName, StringComparison.Ordinal);
        Assert.Empty(page.Weeks[0].Bars);
        Assert.False(page.Weeks[0].HasBars);
    }

    [Fact]
    public void PastThreeLanesADaySaysHowManyMore()
    {
        foreach (var title in new[] { "A", "B", "C", "D", "E" })
        {
            Add(title, Today, Today);
        }

        var page = Page();

        var row = page.Weeks[2];
        Assert.Equal([0, 1, 2], row.Bars.Select(bar => bar.Lane));
        Assert.Equal(["A", "B", "C"], row.Bars.Select(bar => bar.Title));
        var more = Assert.Single(row.More);
        Assert.Equal(4, more.Column);
        Assert.Equal("Calendar.EventsMore(2)", more.Text);
        Assert.Contains("Calendar.CellEvents(5)", page.Cells.Single(cell => cell.Day == Today).AutomationName, StringComparison.Ordinal);
    }

    [Fact]
    public void TheOpenDayListsItsEventsAboveItsTasks()
    {
        var trip = Add("Trip", Today.AddDays(-1), Today.AddDays(1));
        var page = Page();

        page.Open(Today.AddDays(1));

        var listed = Assert.Single(page.DayEvents);
        Assert.Equal("Trip", listed.Title);
        Assert.False(page.IsDayEmpty);
        Assert.Contains("Calendar.CellEventsOne", page.Cells.Single(cell => cell.Day == Today).AutomationName, StringComparison.Ordinal);

        listed.OpenCommand.Execute(null);
        Assert.True(page.Editor!.IsOpen);
        Assert.Equal("Trip", page.Editor.Title);

        page.Open(Today.AddDays(2));
        Assert.Empty(page.DayEvents);
        Assert.True(page.IsDayEmpty);
        Assert.Equal(trip.Id, page.Weeks[2].Bars.Single().Id);
    }

    [Fact]
    public void TheAreaFilterKeepsItsEventsAndATagFilterHidesThemAll()
    {
        var work = planner.Areas.Create("Work")!;
        Add("Offsite", Today, Today.AddDays(1), work.Id);
        Add("Festival", Today, Today);
        var errand = planner.Tasks.Add(ComposerParser.Parse("Buy milk #errand", planner.Time.GetLocalNow().DateTime))!;
        planner.Tasks.Plan(errand.Id, Today);
        var page = Page();
        Assert.Equal(2, page.Weeks[2].Bars.Count);

        page.Filters.SelectedArea = page.Filters.AreaChoices.Single(area => area.Label == "Work");
        Assert.Equal(["Offsite"], page.Weeks[2].Bars.Select(bar => bar.Title));

        page.Filters.ClearCommand.Execute(null);
        page.Filters.SelectedTag = page.Filters.TagChoices.Single(tag => tag.Label == "#errand");
        Assert.All(page.Weeks, week => Assert.False(week.HasBars));
        page.Open(Today);
        Assert.Empty(page.DayEvents);
    }

    [Fact]
    public void TheEditorChangesAnEventAndOnlyKeepsWhatTheServerTakes()
    {
        var work = planner.Areas.Create("Work")!;
        var trip = Add("Trip", Today, Today.AddDays(2));
        var page = Page();
        var editor = page.Editor!;

        page.OpenEvent(trip.Id);
        Assert.True(editor.IsOpen);
        Assert.True(editor.CanDelete);
        Assert.Equal("Event.Edit", editor.Heading);
        Assert.Equal(Today.ToDateTime(TimeOnly.MinValue), editor.FirstDay);
        Assert.Equal(Today.AddDays(2).ToDateTime(TimeOnly.MinValue), editor.LastDay);
        Assert.Null(editor.Area!.Id);

        editor.LastDay = Today.AddDays(-1).ToDateTime(TimeOnly.MinValue);
        Assert.Equal("Event.EndBeforeStart", editor.Problem);
        Assert.False(editor.SaveCommand.CanExecute(null));
        editor.LastDay = Today.AddDays(367).ToDateTime(TimeOnly.MinValue);
        Assert.Equal("Event.TooLong(366)", editor.Problem);
        Assert.False(editor.SaveCommand.CanExecute(null));

        editor.LastDay = Today.AddDays(5).ToDateTime(TimeOnly.MinValue);
        editor.Title = "  ";
        Assert.False(editor.HasProblem);
        Assert.False(editor.SaveCommand.CanExecute(null));
        editor.Title = "Prague";
        editor.Notes = "Hotel near the river";
        editor.Area = editor.AreaChoices.Single(choice => choice.Id == work.Id);
        editor.SaveCommand.Execute(null);

        Assert.False(editor.IsOpen);
        var stored = planner.Events.Get(trip.Id)!;
        Assert.Equal(("Prague", Today.AddDays(5), "Hotel near the river", work.Id), (stored.Title, stored.EndsOn, stored.Notes, stored.AreaId));
        Assert.Equal(3, page.Weeks[2].Bars.Single().Span);
        Assert.Equal(3, page.Weeks[3].Bars.Single().Span);
    }

    [Fact]
    public void ANewEventFromTheEditorTakesTheDaysItWasOpenedOn()
    {
        var page = Page();
        var editor = page.Editor!;

        editor.OpenNew(Today, Today.AddDays(1));
        Assert.Equal("Event.Add", editor.Heading);
        Assert.False(editor.CanDelete);
        editor.Title = "Conference";
        editor.SaveCommand.Execute(null);

        var added = Assert.Single(planner.Events.All());
        Assert.Equal((Today, Today.AddDays(1)), (added.StartsOn, added.EndsOn));
        Assert.Equal("Conference", page.Weeks[2].Bars.Single().Title);
    }

    [Fact]
    public void DeleteClosesTheEditorAndUndoBringsTheEventBackForFiveSeconds()
    {
        var trip = Add("Trip", Today, Today.AddDays(2));
        var other = Add("Talk", Today, Today);
        var page = Page();

        page.OpenEvent(trip.Id);
        page.Editor!.DeleteCommand.Execute(null);

        Assert.False(page.Editor.IsOpen);
        Assert.Null(planner.Events.Get(trip.Id));
        Assert.True(page.HasUndo);
        Assert.Equal("Event.Deleted(Trip)", page.UndoText);
        page.UndoCommand.Execute(null);
        Assert.NotNull(planner.Events.Get(trip.Id));
        Assert.False(page.HasUndo);

        page.OpenEvent(other.Id);
        page.Editor.DeleteCommand.Execute(null);
        planner.Time.Advance(TimeSpan.FromSeconds(6));
        Assert.False(page.HasUndo);
        Assert.Null(planner.Events.Get(other.Id));
    }

    [Fact]
    public void TodayShowsTheEventsGoingOnAndOneOpensItsEditor()
    {
        var trip = Add("Trip", Today.AddDays(-1), Today.AddDays(2));
        Add("Talk", Today, Today);
        Add("Later", Today.AddDays(1), Today.AddDays(3));
        var opened = new List<string>();
        var today = List(ListKind.Today, opened.Add);

        Assert.True(today.HasOngoingEvents);
        Assert.Equal(["Lists.EventDay(Trip,2,4)", "Talk"], today.OngoingEvents.Select(line => line.Text));
        Assert.Equal("Lists.EventDayName(Trip,2,4)", today.OngoingEvents[0].AutomationName);
        today.OngoingEvents[0].OpenCommand.Execute(null);
        Assert.Equal([trip.Id], opened);

        Assert.True(planner.Events.Delete(trip.Id));
        Assert.Equal(["Talk"], today.OngoingEvents.Select(line => line.Text));
        Assert.False(List(ListKind.Tomorrow, opened.Add).HasOngoingEvents);
    }

    [Fact]
    public void AnEventReadsItsDaysTheShortestWayThatIsClear()
    {
        var culture = CultureInfo.CurrentCulture;
        CultureInfo.CurrentCulture = CultureInfo.InvariantCulture;
        try
        {
            var strings = new SpanStrings();
            Assert.Equal("Prague, 12 to 15 October", EventText.Name(Event(new(2026, 10, 12), new(2026, 10, 15)), strings));
            Assert.Equal("Prague, 12 October", EventText.Name(Event(new(2026, 10, 12), new(2026, 10, 12)), strings));
            Assert.Equal("30 September to 2 October", EventText.Days(Event(new(2026, 9, 30), new(2026, 10, 2)), strings));
            Assert.Equal("30 December 2026 to 2 January 2027", EventText.Days(Event(new(2026, 12, 30), new(2027, 1, 2)), strings));
        }
        finally
        {
            CultureInfo.CurrentCulture = culture;
        }

        static EventItem Event(DateOnly first, DateOnly last) => new("e", "Prague", first, last);
    }

    private EventItem Add(string title, DateOnly first, DateOnly last, string? areaId = null) =>
        planner.Events.Add(new EventDraft(title, first, last, AreaId: areaId))!;

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

    private ListViewModel List(ListKind kind, Action<string> openEvent)
    {
        var composer = new ComposerViewModel(
            planner.Tasks, planner.Areas, planner.Tags, planner.Projects, planner.Settings, planner.Strings, planner.Time, _ => null, day => day, action => action());
        return new ListViewModel(
            kind,
            planner.Tasks,
            planner.Areas,
            composer,
            planner.Sync,
            planner.Settings,
            planner.Strings,
            planner.Time,
            _ => null,
            () => true,
            planner.Tick,
            action => action(),
            events: planner.Events,
            openEvent: openEvent);
    }

    // The two sentences an event's name is made of, in English.
    private sealed class SpanStrings : GoalMaker.App.Localization.IStrings
    {
        public string Get(string key, params object[] arguments) => key switch
        {
            "Calendar.EventSpan" => $"{arguments[0]} to {arguments[1]}",
            "Calendar.EventName" => $"{arguments[0]}, {arguments[1]}",
            _ => key,
        };
    }
}
