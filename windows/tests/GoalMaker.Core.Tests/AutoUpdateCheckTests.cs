using System.Text;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Settings;
using GoalMaker.Core.Updates;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.Core.Tests;

/// <summary>
/// The quiet check for updates: a little after the start and once a day after that, never in a dev
/// build, never installing, and silent when it fails.
/// </summary>
public sealed class AutoUpdateCheckTests : IDisposable
{
    private static readonly TimeSpan Delay = TimeSpan.FromSeconds(30);
    private static readonly TimeSpan Look = TimeSpan.FromHours(1);
    private static readonly string Hash = new('a', 64);

    private static readonly byte[] Manifest = Encoding.UTF8.GetBytes(
        "{\"schema\":1,\"version\":\"0.3.0\",\"publishedAt\":\"2026-09-18T12:00:00Z\"," +
        "\"artifacts\":[{\"platform\":\"windows\",\"path\":\"0.3.0/GoalMaker-0.3.0-setup.exe\",\"size\":100,\"sha256\":\"" + Hash + "\"}]}");

    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 10, 1, 9, 0, 0, TimeSpan.Zero));
    private readonly FakeSettings settings = new();
    private readonly FakeChannel channel = new();
    private readonly FakeInstaller installer = new();
    private AutoUpdateCheck? check;

    [Fact]
    public void ChecksSoonAfterTheStartWhenTheLastCheckIsOlderThanADay()
    {
        settings.UpdatesCheckedAt = time.GetUtcNow() - TimeSpan.FromDays(2);
        var (updates, auto) = Start();

        time.Advance(Delay - TimeSpan.FromSeconds(1));
        Assert.Equal(0, channel.Fetches);

        time.Advance(TimeSpan.FromSeconds(1));
        Assert.Equal(1, channel.Fetches);
        Assert.NotNull(updates.Waiting);
        Assert.Equal(time.GetUtcNow(), auto.LastChecked);
        Assert.Equal("0.3.0", settings.UpdateFound);
    }

    [Fact]
    public void ChecksOnTheFirstStartEver()
    {
        Start();

        time.Advance(Delay);

        Assert.Equal(1, channel.Fetches);
    }

    [Fact]
    public void SkipsWhenTheLastCheckIsRecent()
    {
        settings.UpdatesCheckedAt = time.GetUtcNow() - TimeSpan.FromHours(2);
        var (updates, auto) = Start();

        time.Advance(Delay + TimeSpan.FromHours(3));

        Assert.Equal(0, channel.Fetches);
        Assert.False(auto.IsDue());
        Assert.Null(updates.Waiting);
    }

    [Fact]
    public void ChecksAgainADayLater()
    {
        Start();
        time.Advance(Delay);
        Assert.Equal(1, channel.Fetches);

        time.Advance(TimeSpan.FromHours(23));
        Assert.Equal(1, channel.Fetches);

        time.Advance(TimeSpan.FromHours(1));
        Assert.Equal(2, channel.Fetches);
    }

    [Fact]
    public void NeverDownloadsOrInstalls()
    {
        var (updates, _) = Start();

        time.Advance(Delay + TimeSpan.FromDays(3));

        Assert.NotNull(updates.Waiting);
        Assert.Equal(0, channel.Downloads);
        Assert.Empty(installer.Launched);
    }

    [Fact]
    public void AFailureIsQuietAndTriedAgainAtTheNextLook()
    {
        channel.Reachable = false;
        var (updates, auto) = Start();
        var results = new List<UpdateCheckResult>();
        auto.Checked += (_, result) => results.Add(result);

        time.Advance(Delay);

        Assert.IsType<UpdateCheckResult.Failed>(Assert.Single(results));
        Assert.Null(auto.LastChecked);
        Assert.Null(updates.Waiting);

        channel.Reachable = true;
        time.Advance(Look);

        Assert.Equal(2, channel.Fetches);
        Assert.NotNull(updates.Waiting);
        Assert.NotNull(auto.LastChecked);
    }

    [Fact]
    public void AQuietFailureKeepsTheMark()
    {
        var (updates, auto) = Start();
        time.Advance(Delay);
        var waiting = updates.Waiting;
        Assert.NotNull(waiting);
        var found = auto.LastChecked;
        var results = new List<UpdateCheckResult>();
        auto.Checked += (_, result) => results.Add(result);

        channel.Reachable = false;
        time.Advance(TimeSpan.FromDays(1));

        // Only a check that gets an answer replaces the waiting update.
        Assert.NotEmpty(results);
        Assert.All(results, result => Assert.IsType<UpdateCheckResult.Failed>(result));
        Assert.Same(waiting, updates.Waiting);
        Assert.Equal(found, auto.LastChecked);
        Assert.Equal("0.3.0", settings.UpdateFound);
        Assert.Empty(installer.Launched);
    }
    [Fact]
    public void AnUpdateFoundBeforeARestartIsCheckedAgainToShowTheMark()
    {
        settings.UpdatesCheckedAt = time.GetUtcNow() - TimeSpan.FromHours(1);
        settings.UpdateFound = "0.3.0";
        var (updates, auto) = Start();
        Assert.True(auto.IsDue());

        time.Advance(Delay);

        Assert.Equal(1, channel.Fetches);
        Assert.NotNull(updates.Waiting);
        Assert.False(auto.IsDue());
    }

    [Fact]
    public void AnUpToDateCheckForgetsTheUpdateItFoundBefore()
    {
        settings.UpdatesCheckedAt = time.GetUtcNow() - TimeSpan.FromHours(1);
        settings.UpdateFound = "0.3.0";
        var (updates, auto) = Start(installed: "0.3.0");

        time.Advance(Delay);

        Assert.Null(updates.Waiting);
        Assert.Null(settings.UpdateFound);
        Assert.False(auto.IsDue());
    }

    [Theory]
    [InlineData("0.2.0-dev", true)]
    [InlineData("0.2.0", false)]
    public async Task BuildsWithoutAChannelNeverCheck(string installed, bool configured)
    {
        var (_, auto) = Start(installed, configured);

        time.Advance(TimeSpan.FromDays(3));

        Assert.False(auto.IsDue());
        Assert.Null(await auto.CheckIfDueAsync(TestContext.Current.CancellationToken));
        Assert.Equal(0, channel.Fetches);
        Assert.Null(settings.UpdatesCheckedAt);
    }

    [Fact]
    public async Task ACheckTheOwnerAsksForIsWrittenDownToo()
    {
        settings.UpdatesCheckedAt = time.GetUtcNow() - TimeSpan.FromHours(2);
        var (_, auto) = Start();

        var result = await auto.CheckNowAsync(TestContext.Current.CancellationToken);

        Assert.IsType<UpdateCheckResult.Available>(result);
        Assert.Equal(time.GetUtcNow(), settings.UpdatesCheckedAt);
    }

    public void Dispose() => check?.Dispose();

    private (UpdateService Updates, AutoUpdateCheck Auto) Start(string installed = "0.2.0", bool configured = true)
    {
        var updates = new UpdateService(
            installed, ReleasePlatform.Windows, configured, channel, new ReleaseVerifier(new FakeSignatures()), installer);
        check = new AutoUpdateCheck(updates, settings, time, AutoUpdateCheck.Daily);
        check.Start(Delay, Look);
        return (updates, check);
    }

    private sealed class FakeSignatures : ISignatureVerifier
    {
        public bool Verify(ReadOnlySpan<byte> data, string signatureBase64) => signatureBase64 == "good";
    }

    private sealed class FakeChannel : IReleaseChannel
    {
        public bool Reachable { get; set; } = true;

        public int Fetches { get; private set; }

        public int Downloads { get; private set; }

        public Task<ChannelSnapshot> FetchLatestAsync(CancellationToken cancellationToken)
        {
            Fetches++;
            return Reachable
                ? Task.FromResult(new ChannelSnapshot(Manifest, "good"))
                : Task.FromException<ChannelSnapshot>(new HttpRequestException("offline"));
        }

        public Task<DownloadedArtifact> DownloadAsync(string path, IProgress<long>? progress, CancellationToken cancellationToken)
        {
            Downloads++;
            return Task.FromResult(new DownloadedArtifact(@"C:\updates\setup.exe", 100, Hash));
        }
    }

    private sealed class FakeInstaller : IUpdateInstaller
    {
        public List<string> Launched { get; } = [];

        public void Launch(string localPath) => Launched.Add(localPath);
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

        public TimeOnly? WantsReadyReminder { get; set; }

        public WhyFrequency WhyReminder { get; set; } = WhyFrequency.Off;

        public DateTimeOffset? RemindedUntil { get; set; }

        public DateTimeOffset? SignedInAt { get; set; }

        public string? QuickAddHotkey { get; set; }

        public ComposerMode ComposerMode { get; set; }

        public bool NavigationCollapsed { get; set; }

        public IReadOnlyList<string> PinnedPlaces { get; set; } = [];

        public IReadOnlyList<string> FoldedBoardColumns { get; set; } = [];

        public GoalsView GoalsView { get; set; }

        public WantsTab WantsTab { get; set; }

        public int? NewYearDismissed { get; set; }

        public Core.Backend.BackendEnvironment? BackendOverride { get; set; }

        public WindowPlacement? MainWindowPlacement { get; set; }

        public IReadOnlyDictionary<string, MiniWindowState> MiniWindows { get; set; } =
            new Dictionary<string, MiniWindowState>(StringComparer.Ordinal);

        public string? WeeklyBackupFolder { get; set; }

        public DateTimeOffset? WeeklyBackupWritten { get; set; }

        public DateTimeOffset? UpdatesCheckedAt { get; set; }

        public string? UpdateFound { get; set; }

        public bool TallyOn { get; set; }

        public string DeviceId { get; } = Guid.NewGuid().ToString();
    }
}
