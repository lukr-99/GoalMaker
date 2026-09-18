using System.IO;
using System.Text.Json.Nodes;
using GoalMaker.App.Localization;
using GoalMaker.App.Shell;
using GoalMaker.Core.Backend;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;
using GoalMaker.Core.Sync;
using GoalMaker.Infrastructure.Replica;
using GoalMaker.Infrastructure.Sync;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.App.Tests;

/// <summary>
/// The planner's data layer for view-model tests: a real replica on a throwaway file, fake time
/// (Friday 18 September 2026, 14:00, UTC) and settings, and no server.
/// </summary>
internal sealed class TestPlanner : IDisposable
{
    public const string Owner = "11111111-1111-1111-1111-111111111111";
    private readonly string folder = Path.Combine(Path.GetTempPath(), "goalmaker-tests", Guid.NewGuid().ToString("N"));
    private readonly SqliteReplica replica;

    public TestPlanner()
    {
        Time.SetLocalTimeZone(TimeZoneInfo.Utc);
        var catalog = ContractResources.SyncedTables();
        replica = new SqliteReplica(Path.Combine(folder, "replica.db"), catalog, ReplicaMigrator.BuiltIn());
        Sync = new SyncCoordinator(new SyncEngine(catalog, replica, new NoRemote(), Time), replica, Time, TimeSpan.FromSeconds(2));
        var rows = new NewRows(catalog, () => Owner, Time);
        Areas = new AreaList(replica, rows, ["violet", "blue"], () => { });
        Tags = new TagList(replica, rows, () => { });
        Tasks = new TaskList(replica, rows, Areas, Tags, () => { }, () => PlanningDay.Of(Time.GetLocalNow().DateTime, Settings.DayStartHour));
        Reminders = new ReminderList(replica, rows, () => { }, () => TimeZoneInfo.Utc);
    }

    public FakeTimeProvider Time { get; } = new(new DateTimeOffset(2026, 9, 18, 14, 0, 0, TimeSpan.Zero));

    public FakeSettings Settings { get; } = new();

    public FormatStrings Strings { get; } = new();

    public SyncCoordinator Sync { get; }

    public AreaList Areas { get; }

    public TagList Tags { get; }

    public TaskList Tasks { get; }

    public ReminderList Reminders { get; }

    public TickSound Tick { get; } = new();

    public TaskItem Task(string title) => Tasks.All().Single(task => task.Title == title);

    public void Dispose()
    {
        Sync.Dispose();
        replica.Dispose();
        Tick.Dispose();
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

    /// <summary>Text as its key, with any arguments in brackets: "Plan.Outcome(1 to tomorrow)".</summary>
    internal sealed class FormatStrings : IStrings
    {
        public string Get(string key, params object[] arguments) =>
            arguments.Length == 0 ? key : $"{key}({string.Join(",", arguments)})";
    }

    internal sealed class FakeSettings : ISettingsStore
    {
        public Appearance Appearance { get; set; } = Appearance.Default;

        public int DayStartHour { get; set; } = PlanningDay.DefaultStartHour;

        public QuietHours QuietHours { get; set; } = QuietHours.Off;

        public DateTimeOffset? RemindedUntil { get; set; }

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
