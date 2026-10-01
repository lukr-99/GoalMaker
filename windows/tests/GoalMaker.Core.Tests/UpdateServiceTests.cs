using System.Text;
using GoalMaker.Core.Updates;

namespace GoalMaker.Core.Tests;

public sealed class UpdateServiceTests
{
    private static readonly string Hash = new('a', 64);

    private static readonly byte[] Manifest = Encoding.UTF8.GetBytes(
        "{\"schema\":1,\"version\":\"0.3.0\",\"publishedAt\":\"2026-09-18T12:00:00Z\"," +
        "\"artifacts\":[{\"platform\":\"windows\",\"path\":\"0.3.0/GoalMaker-0.3.0-setup.exe\",\"size\":100,\"sha256\":\"" + Hash + "\"}]}");

    private static UpdateService Service(
        string installed = "0.2.0",
        bool configured = true,
        FakeChannel? channel = null,
        FakeInstaller? installer = null) =>
        new(
            installed,
            ReleasePlatform.Windows,
            configured,
            channel ?? new FakeChannel(new ChannelSnapshot(Manifest, "good")),
            new ReleaseVerifier(new FakeSignatures()),
            installer ?? new FakeInstaller());

    [Fact]
    public async Task AnUnconfiguredChannelIsReportedNotContacted()
    {
        var channel = new FakeChannel(null);
        Assert.IsType<UpdateCheckResult.NotConfigured>(await Service(configured: false, channel: channel).CheckAsync(TestContext.Current.CancellationToken));
        Assert.Equal(0, channel.Fetches);
    }

    [Fact]
    public async Task DevelopmentBuildsNeverUpdateThemselves() =>
        Assert.IsType<UpdateCheckResult.DevelopmentBuild>(await Service(installed: "0.2.0-dev").CheckAsync(TestContext.Current.CancellationToken));

    [Fact]
    public async Task ANewerSignedReleaseIsOffered()
    {
        var available = Assert.IsType<UpdateCheckResult.Available>(await Service().CheckAsync(TestContext.Current.CancellationToken));
        Assert.Equal("0.3.0", available.Manifest.Version.ToString());
    }

    [Fact]
    public async Task TheSameVersionIsUpToDate() =>
        Assert.Equal(new UpdateCheckResult.UpToDate("0.3.0"), await Service(installed: "0.3.0").CheckAsync(TestContext.Current.CancellationToken));

    [Fact]
    public async Task ABadlySignedManifestIsUntrusted()
    {
        var channel = new FakeChannel(new ChannelSnapshot(Manifest, "forged"));
        Assert.IsType<UpdateCheckResult.Untrusted>(await Service(channel: channel).CheckAsync(TestContext.Current.CancellationToken));
    }

    [Fact]
    public async Task ANetworkFailureIsReported() =>
        Assert.Equal(new UpdateCheckResult.Failed("offline"), await Service(channel: new FakeChannel(null)).CheckAsync(TestContext.Current.CancellationToken));

    [Fact]
    public async Task AVerifiedDownloadStartsTheInstaller()
    {
        var installer = new FakeInstaller();
        var service = Service(installer: installer);
        var available = Assert.IsType<UpdateCheckResult.Available>(await service.CheckAsync(TestContext.Current.CancellationToken));
        Assert.IsType<InstallResult.InstallerStarted>(await service.InstallAsync(available, null, TestContext.Current.CancellationToken));
        Assert.Equal([@"C:\updates\setup.exe"], installer.Launched);
    }

    [Theory]
    [InlineData(100, "b")]
    [InlineData(99, "a")]
    public async Task AMismatchedDownloadIsNeverInstalled(long size, string hashCharacter)
    {
        var installer = new FakeInstaller();
        var channel = new FakeChannel(
            new ChannelSnapshot(Manifest, "good"),
            new DownloadedArtifact(@"C:\updates\setup.exe", size, new string(hashCharacter[0], 64)));
        var service = Service(channel: channel, installer: installer);
        var available = Assert.IsType<UpdateCheckResult.Available>(await service.CheckAsync(TestContext.Current.CancellationToken));
        Assert.IsType<InstallResult.DownloadCorrupted>(await service.InstallAsync(available, null, TestContext.Current.CancellationToken));
        Assert.Empty(installer.Launched);
    }

    [Fact]
    public async Task AFoundUpdateWaitsThroughAFailedCheckUntilACheckAnswersWithoutOne()
    {
        var channel = new FakeChannel(new ChannelSnapshot(Manifest, "good"));
        var service = Service(channel: channel);
        var changes = 0;
        service.WaitingChanged += (_, _) => changes++;
        Assert.Null(service.Waiting);

        var available = await service.CheckAsync(TestContext.Current.CancellationToken);

        Assert.Same(available, service.Waiting);
        Assert.Equal(1, changes);

        channel.Snapshot = null;
        Assert.IsType<UpdateCheckResult.Failed>(await service.CheckAsync(TestContext.Current.CancellationToken));

        Assert.Same(available, service.Waiting);
        Assert.Equal(1, changes);

        channel.Snapshot = new ChannelSnapshot(Manifest, "forged");
        await service.CheckAsync(TestContext.Current.CancellationToken);

        Assert.Null(service.Waiting);
        Assert.Equal(2, changes);
    }

    [Fact]
    public async Task NothingWaitsWhenUpToDateOrUntrusted()
    {
        var current = Service(installed: "0.3.0");
        await current.CheckAsync(TestContext.Current.CancellationToken);
        Assert.Null(current.Waiting);

        var forged = Service(channel: new FakeChannel(new ChannelSnapshot(Manifest, "forged")));
        await forged.CheckAsync(TestContext.Current.CancellationToken);
        Assert.Null(forged.Waiting);
    }

    [Fact]
    public async Task AnInstallThatFailsLeavesTheUpdateWaiting()
    {
        var channel = new FakeChannel(
            new ChannelSnapshot(Manifest, "good"),
            new DownloadedArtifact(@"C:\updates\setup.exe", 100, new string('b', 64)));
        var service = Service(channel: channel);
        var available = Assert.IsType<UpdateCheckResult.Available>(await service.CheckAsync(TestContext.Current.CancellationToken));

        await service.InstallAsync(available, null, TestContext.Current.CancellationToken);

        Assert.Same(available, service.Waiting);
    }

    private sealed class FakeSignatures : ISignatureVerifier
    {
        public bool Verify(ReadOnlySpan<byte> data, string signatureBase64) => signatureBase64 == "good";
    }

    private sealed class FakeChannel(ChannelSnapshot? snapshot, DownloadedArtifact? downloaded = null) : IReleaseChannel
    {
        public int Fetches { get; private set; }

        /// <summary>What the channel holds now; null means it can't be reached.</summary>
        public ChannelSnapshot? Snapshot { get; set; } = snapshot;

        public Task<ChannelSnapshot> FetchLatestAsync(CancellationToken cancellationToken)
        {
            Fetches++;
            return Snapshot is null ? throw new HttpRequestException("offline") : Task.FromResult(Snapshot);
        }

        public Task<DownloadedArtifact> DownloadAsync(string path, IProgress<long>? progress, CancellationToken cancellationToken) =>
            Task.FromResult(downloaded ?? new DownloadedArtifact(@"C:\updates\setup.exe", 100, Hash));
    }

    private sealed class FakeInstaller : IUpdateInstaller
    {
        public List<string> Launched { get; } = [];

        public void Launch(string localPath) => Launched.Add(localPath);
    }
}
