using System.IO;
using System.Text.Json.Nodes;
using GoalMaker.App.Localization;
using GoalMaker.App.Shell;
using GoalMaker.App.ViewModels;
using GoalMaker.Core.Backend;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;
using GoalMaker.Core.Sync;
using GoalMaker.Infrastructure.Replica;
using GoalMaker.Infrastructure.Sync;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.App.Tests;

/// <summary>The lists as the Windows app shows them (docs/lists.md), over a real replica and without a window.</summary>
public sealed class ListViewModelTests : IDisposable
{
    private const string Owner = "11111111-1111-1111-1111-111111111111";
    private readonly string folder = Path.Combine(Path.GetTempPath(), "goalmaker-tests", Guid.NewGuid().ToString("N"));
    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 9, 18, 14, 0, 0, TimeSpan.Zero));
    private readonly FakeSettings settings = new();
    private readonly SqliteReplica replica;
    private readonly SyncCoordinator sync;
    private readonly AreaList areas;
    private readonly TagList tags;
    private readonly TaskList tasks;
    private readonly TickSound tick = new();

    public ListViewModelTests()
    {
        time.SetLocalTimeZone(TimeZoneInfo.Utc);
        var catalog = ContractResources.SyncedTables();
        replica = new SqliteReplica(Path.Combine(folder, "replica.db"), catalog, ReplicaMigrator.BuiltIn());
        sync = new SyncCoordinator(new SyncEngine(catalog, replica, new NoRemote(), time), replica, time, TimeSpan.FromSeconds(2));
        var rows = new NewRows(catalog, () => Owner, time);
        areas = new AreaList(replica, rows, ["violet", "blue"], () => { });
        tags = new TagList(replica, rows, () => { });
        tasks = new TaskList(replica, rows, areas, tags, () => { });
    }

    public void Dispose()
    {
        sync.Dispose();
        replica.Dispose();
        tick.Dispose();
        Microsoft.Data.Sqlite.SqliteConnection.ClearAllPools();
        try
        {
            Directory.Delete(folder, recursive: true);
        }
        catch (IOException)
        {
            // Best effort on Windows; the temp folder is cleaned eventually.
        }
    }

    [Fact]
    public void TodayGroupsPrioritiesScheduledAndMore()
    {
        var today = List(ListKind.Today);
        Add(today, "Water plants !");
        Add(today, "Review budget 18:00");
        Add(today, "Buy milk");

        Assert.Equal(["LISTS.PRIORITIES", "LISTS.SCHEDULED", "LISTS.MORE"], today.Sections.Select(section => section.Header));
        Assert.Equal(["Water plants", "Review budget", "Buy milk"], today.Sections.Select(section => section.Rows.Single().Title));
        Assert.Equal("18:00", today.Sections[1].Rows[0].TimeText);
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
        Assert.Equal("Lists.Done", today.UndoText);

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
        time.Advance(TimeSpan.FromSeconds(5));
        Assert.False(inbox.HasUndo);
        Assert.Single(inbox.Sections[0].Rows);
    }

    [Fact]
    public void OverdueIsFoldedAndStaysOpenOnceOpened()
    {
        var today = List(ListKind.Today);
        Add(today, "File the receipts");
        time.Advance(TimeSpan.FromDays(2));
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
        time.SetUtcNow(new DateTimeOffset(2026, 9, 19, 2, 30, 0, TimeSpan.Zero));
        var today = List(ListKind.Today);
        Add(today, "Late thought");
        Assert.Equal(new DateOnly(2026, 9, 18), Task("Late thought").PlannedDate);

        settings.DayStartHour = 0;
        today.Refresh();
        Assert.True(today.Sections.Single().IsCollapsible);
    }

    private ListViewModel List(ListKind kind)
    {
        Func<DateOnly, DateOnly?> defaultDay = kind switch
        {
            ListKind.Today => day => day,
            ListKind.Tomorrow => day => day.AddDays(1),
            _ => _ => null,
        };
        var strings = new KeyStrings();
        var composer = new ComposerViewModel(tasks, areas, tags, settings, strings, time, _ => null, defaultDay, action => action());
        return new ListViewModel(kind, tasks, areas, composer, sync, settings, strings, time, _ => null, () => true, tick, action => action());
    }

    private static void Add(ListViewModel list, string line)
    {
        list.Composer.NewTaskTitle = line;
        list.Composer.AddTaskCommand.Execute(null);
        Assert.Equal(string.Empty, list.Composer.NewTaskTitle);
    }

    private TaskItem Task(string title) => tasks.All().Single(task => task.Title == title);

    private sealed class KeyStrings : IStrings
    {
        public string Get(string key, params object[] arguments) => key;
    }

    private sealed class FakeSettings : ISettingsStore
    {
        public Appearance Appearance { get; set; } = Appearance.Default;

        public int DayStartHour { get; set; } = PlanningDay.DefaultStartHour;

        public bool NavigationCollapsed { get; set; }

        public BackendEnvironment? BackendOverride { get; set; }

        public WindowPlacement? MainWindowPlacement { get; set; }
    }

    private sealed class NoRemote : IRemoteTables
    {
        public Task<JsonObject> UpsertAsync(string table, JsonObject row, CancellationToken cancellationToken) =>
            throw new RemoteUnavailableException("offline");

        public Task<IReadOnlyList<JsonObject>> PullAsync(string table, string? from, RowCursor? after, int limit, CancellationToken cancellationToken) =>
            throw new RemoteUnavailableException("offline");
    }
}
