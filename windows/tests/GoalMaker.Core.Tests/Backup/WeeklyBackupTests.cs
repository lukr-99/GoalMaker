using GoalMaker.Core.Backup;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;
using GoalMaker.Core.Tests.Sync;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.Core.Tests.Backup;

/// <summary>
/// The weekly automatic export (M6-03, story 92): when one is due, what a folder that has gone away
/// does, and how many files are kept.
/// </summary>
public sealed class WeeklyBackupTests : IDisposable
{
    private static readonly DateOnly Day = new(2026, 9, 18);
    private readonly TestReplica test = new();
    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 9, 18, 12, 0, 0, TimeSpan.Zero));
    private readonly FakeSettings settings = new();
    private readonly FakeFolder folder = new();
    private readonly WeeklyBackup weekly;

    public WeeklyBackupTests()
    {
        var rows = new NewRows(test.Catalog, () => TestReplica.Owner, time);
        var areas = new AreaList(test.Replica, rows, ["violet"], () => { });
        var tags = new TagList(test.Replica, rows, () => { });
        var projects = new ProjectList(test.Replica, rows, () => { });
        var tasks = new TaskList(test.Replica, rows, areas, tags, projects, () => { }, () => Day);
        tasks.Add(ComposerParser.Parse("Call the bank", new DateTime(2026, 9, 18, 9, 0, 0)));
        var backup = new BackupService(test.Catalog, test.Replica, () => TestReplica.Owner, "1.0.0", "windows", time);
        weekly = new WeeklyBackup(backup, settings, time, folder);
        settings.WeeklyBackupFolder = @"D:\Backups";
    }

    public void Dispose() => test.Dispose();

    [Fact]
    public void WithNoFolderThereIsNothingToDo()
    {
        settings.WeeklyBackupFolder = null;

        Assert.Equal(WeeklyBackupResult.Off, weekly.Run());
        Assert.Empty(folder.Files);
    }

    [Fact]
    public void TheFirstRunWritesOne()
    {
        Assert.Equal(WeeklyBackupResult.Written, weekly.Run());

        var (name, text) = Assert.Single(folder.Files);
        Assert.Equal("goalmaker-2026-09-18.json", name);
        Assert.Contains("\"format\": \"goalmaker.backup\"", text, StringComparison.Ordinal);
        Assert.Equal(time.GetUtcNow(), settings.WeeklyBackupWritten);
    }

    [Fact]
    public void ASecondRunTheSameDayWritesNothing()
    {
        weekly.Run();
        time.Advance(TimeSpan.FromDays(3));

        Assert.Equal(WeeklyBackupResult.NotDue, weekly.Run());
        Assert.Single(folder.Files);
    }

    [Fact]
    public void AWeekLaterWritesAnother()
    {
        weekly.Run();
        time.Advance(TimeSpan.FromDays(7));

        Assert.Equal(WeeklyBackupResult.Written, weekly.Run());
        Assert.Equal(2, folder.Files.Count);
        Assert.Contains("goalmaker-2026-09-25.json", folder.Files.Keys);
    }

    [Fact]
    public void APcThatWasOffForThreeWeeksWritesOneAtTheNextStart()
    {
        weekly.Run();
        time.Advance(TimeSpan.FromDays(21));

        Assert.Equal(WeeklyBackupResult.Written, weekly.Run());

        Assert.Equal(2, folder.Files.Count);
        Assert.Equal(WeeklyBackupResult.NotDue, weekly.Run());
    }

    [Fact]
    public void AFolderThatIsGoneTurnsTheWeeklyExportOff()
    {
        folder.Missing = true;

        Assert.Equal(WeeklyBackupResult.FolderGone, weekly.Run());

        Assert.Null(settings.WeeklyBackupFolder);
        Assert.Equal(WeeklyBackupResult.Off, weekly.Run());
    }

    [Fact]
    public void AFolderThatRefusesTheWriteKeepsTheWeekOpen()
    {
        folder.Refuse = true;

        Assert.Equal(WeeklyBackupResult.CouldNotWrite, weekly.Run());

        Assert.Null(settings.WeeklyBackupWritten);
        folder.Refuse = false;
        Assert.Equal(WeeklyBackupResult.Written, weekly.Run());
    }

    [Fact]
    public void OnlyTheLastFewFilesAreKept()
    {
        for (var week = 0; week < WeeklyBackupRules.Keep + 3; week++)
        {
            Assert.Equal(WeeklyBackupResult.Written, weekly.Run());
            time.Advance(TimeSpan.FromDays(7));
        }

        Assert.Equal(WeeklyBackupRules.Keep, folder.Files.Count);
        Assert.DoesNotContain("goalmaker-2026-09-18.json", folder.Files.Keys);
        Assert.Contains("goalmaker-2026-11-27.json", folder.Files.Keys);
    }

    private sealed class FakeFolder : IBackupFolder
    {
        public Dictionary<string, string> Files { get; } = new(StringComparer.Ordinal);

        public bool Missing { get; set; }

        public bool Refuse { get; set; }

        public bool Exists(string folder) => !Missing;

        public bool Write(string folder, string name, string text)
        {
            if (Refuse)
            {
                return false;
            }

            Files[name] = text;
            return true;
        }

        public IReadOnlyList<string> Exports(string folder) => [.. Files.Keys];

        public void Remove(string folder, string name) => Files.Remove(name);
    }

    private sealed class FakeSettings : ISettingsStore
    {
        public Appearance Appearance { get; set; } = Appearance.Default;

        public int DayStartHour { get; set; } = PlanningDay.DefaultStartHour;

        public QuietHours QuietHours { get; set; } = QuietHours.Off;

        public TimeOnly? PlanTomorrowReminder { get; set; }

        public TimeOnly? WeeklyReviewReminder { get; set; }

        public int WeeklyReviewWeekday { get; set; } = 7;

        public TimeOnly? MonthlyReviewReminder { get; set; }

        public DateTimeOffset? RemindedUntil { get; set; }

        public string? QuickAddHotkey { get; set; }

        public bool NavigationCollapsed { get; set; }

        public Core.Backend.BackendEnvironment? BackendOverride { get; set; }

        public WindowPlacement? MainWindowPlacement { get; set; }

        public IReadOnlyDictionary<string, MiniWindowState> MiniWindows { get; set; } =
            new Dictionary<string, MiniWindowState>(StringComparer.Ordinal);

        public string? WeeklyBackupFolder { get; set; }

        public DateTimeOffset? WeeklyBackupWritten { get; set; }
    }
}
