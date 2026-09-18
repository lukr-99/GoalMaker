using System.Globalization;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Planning;

namespace GoalMaker.App.Tests;

/// <summary>The lists as the Windows app shows them (docs/lists.md), over a real replica and without a window.</summary>
public sealed class ListViewModelTests : IDisposable
{
    private readonly TestPlanner planner = new();

    public void Dispose() => planner.Dispose();

    [Fact]
    public void TodayGroupsPrioritiesScheduledAndMore()
    {
        var today = List(ListKind.Today);
        Add(today, "Water plants !");
        Add(today, "Review budget 18:00");
        Add(today, "Buy milk");

        Assert.Equal(["LISTS.PRIORITIES", "LISTS.SCHEDULED", "LISTS.MORE"], today.Sections.Select(section => section.Header));
        Assert.Equal(["Water plants", "Review budget", "Buy milk"], today.Sections.Select(section => section.Rows.Single().Title));
        Assert.Equal(new TimeOnly(18, 0).ToString("t", CultureInfo.CurrentCulture), today.Sections[1].Rows[0].TimeText);
        Assert.True(today.Sections[0].Rows[0].TopPriority);
        Assert.False(today.IsEmpty);
    }

    [Fact]
    public void AListWithOnlyUnsortedTasksHasNoHeader()
    {
        var today = List(ListKind.Today);
        Add(today, "Buy milk");

        Assert.Equal(string.Empty, today.Sections.Single().Header);
        Assert.False(today.Sections.Single().HasPlainHeader);
    }

    [Fact]
    public void EachListsComposerPutsALineOnItsOwnDay()
    {
        var today = List(ListKind.Today);
        var tomorrow = List(ListKind.Tomorrow);
        var inbox = List(ListKind.Inbox);

        Add(today, "Call the bank");
        Add(tomorrow, "Pack bags");
        Add(inbox, "Read about sourdough");
        Add(inbox, "Dentist tomorrow");

        Assert.Equal(new DateOnly(2026, 9, 18), Task("Call the bank").PlannedDate);
        Assert.Equal(new DateOnly(2026, 9, 19), Task("Pack bags").PlannedDate);
        Assert.Null(Task("Read about sourdough").PlannedDate);
        Assert.Equal(new DateOnly(2026, 9, 19), Task("Dentist").PlannedDate);
        Assert.Equal(["Pack bags", "Dentist"], tomorrow.Sections.SelectMany(section => section.Rows).Select(row => row.Title));
        Assert.Equal(["Read about sourdough"], inbox.Sections.SelectMany(section => section.Rows).Select(row => row.Title));
    }

    [Fact]
    public void CheckingATaskCompletesItWithUndo()
    {
        var today = List(ListKind.Today);
        Add(today, "Buy milk");

        today.Sections[0].Rows[0].IsDone = true;

        Assert.Equal(TaskState.Done, Task("Buy milk").State);
        Assert.True(today.IsEmpty);
        Assert.True(today.HasUndo);
        Assert.Equal("Lists.Done(Buy milk)", today.UndoText);

        today.UndoCommand.Execute(null);

        Assert.Equal(TaskState.Open, Task("Buy milk").State);
        Assert.Equal(["Buy milk"], today.Sections.SelectMany(section => section.Rows).Select(row => row.Title));
        Assert.False(today.HasUndo);
    }

    [Fact]
    public void DeletingOffersUndoForFiveSeconds()
    {
        var inbox = List(ListKind.Inbox);
        Add(inbox, "Old idea");
        Add(inbox, "Other idea");

        inbox.Sections[0].Rows[0].DeleteCommand.Execute(null);
        Assert.True(inbox.HasUndo);
        inbox.UndoCommand.Execute(null);
        Assert.Equal(2, inbox.Sections[0].Rows.Count);

        inbox.Sections[0].Rows[0].DeleteCommand.Execute(null);
        planner.Time.Advance(TimeSpan.FromSeconds(5));
        Assert.False(inbox.HasUndo);
        Assert.Single(inbox.Sections[0].Rows);
    }

    [Fact]
    public void OverdueIsFoldedAndStaysOpenOnceOpened()
    {
        var today = List(ListKind.Today);
        Add(today, "File the receipts");
        planner.Time.Advance(TimeSpan.FromDays(2));
        today.Refresh();

        var overdue = today.Sections.Single();
        Assert.True(overdue.IsCollapsible);
        Assert.False(overdue.IsExpanded);
        Assert.True(today.IsEmpty);
        Assert.NotEmpty(overdue.Rows[0].DayText);

        overdue.IsExpanded = true;
        today.Refresh();
        Assert.True(today.Sections.Single().IsExpanded);
    }

    [Fact]
    public void ThePlanningDayStartsAtTheStartHour()
    {
        planner.Time.SetUtcNow(new DateTimeOffset(2026, 9, 19, 2, 30, 0, TimeSpan.Zero));
        var today = List(ListKind.Today);
        Add(today, "Late thought");
        Assert.Equal(new DateOnly(2026, 9, 18), Task("Late thought").PlannedDate);

        planner.Settings.DayStartHour = 0;
        today.Refresh();
        Assert.True(today.Sections.Single().IsCollapsible);
    }

    [Fact]
    public void ATaskWithATimeOffersToCountBackAndMarksAWaitingReminder()
    {
        var reminders = new ReminderService(planner.Reminders, planner.Tasks, new NoTimer(), planner.Settings, planner.Time);
        var today = List(ListKind.Today, reminders);
        Add(today, "Call the bank 17:00");
        var row = today.Sections.Single().Rows.Single();

        Assert.False(row.HasReminder);
        Assert.Equal(
            ["Reminder.WhenDue", "Reminder.QuarterBefore", "Reminder.HourBefore", "Reminder.InAnHour", "Reminder.TomorrowMorning"],
            row.ReminderChoices.Select(choice => choice.Label));

        row.ReminderChoices[1].Command.Execute(null);

        var reminded = today.Sections.Single().Rows.Single();
        Assert.True(reminded.HasReminder);
        Assert.Equal("Reminder.RemoveBefore(15)", reminded.ReminderChoices[^1].Label);

        reminded.ReminderChoices[^1].Command.Execute(null);
        Assert.False(today.Sections.Single().Rows.Single().HasReminder);
    }

    [Fact]
    public void ATaskWithoutATimeOnlyOffersTimesOfItsOwn()
    {
        var reminders = new ReminderService(planner.Reminders, planner.Tasks, new NoTimer(), planner.Settings, planner.Time);
        var inbox = List(ListKind.Inbox, reminders);
        Add(inbox, "Buy milk");

        Assert.Equal(
            ["Reminder.InAnHour", "Reminder.TomorrowMorning"],
            inbox.Sections.Single().Rows.Single().ReminderChoices.Select(choice => choice.Label));
    }

    private ListViewModel List(ListKind kind, ReminderService? reminders = null)
    {
        Func<DateOnly, DateOnly?> defaultDay = kind switch
        {
            ListKind.Today => day => day,
            ListKind.Tomorrow => day => day.AddDays(1),
            _ => _ => null,
        };
        var composer = new ComposerViewModel(
            planner.Tasks, planner.Areas, planner.Tags, planner.Settings, planner.Strings, planner.Time, _ => null, defaultDay, action => action());
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
            reminders: reminders);
    }

    // A second apart, so creation order breaks ties the way the test reads.
    private void Add(ListViewModel list, string line)
    {
        list.Composer.NewTaskTitle = line;
        list.Composer.AddTaskCommand.Execute(null);
        Assert.Equal(string.Empty, list.Composer.NewTaskTitle);
        planner.Time.Advance(TimeSpan.FromSeconds(1));
    }

    private TaskItem Task(string title) => planner.Task(title);

    private sealed class NoTimer : IReminderScheduler
    {
        public void ArmAt(DateTime at)
        {
        }

        public void Cancel()
        {
        }
    }
}
