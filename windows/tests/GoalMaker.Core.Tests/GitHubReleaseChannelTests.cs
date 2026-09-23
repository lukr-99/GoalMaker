using System.Net;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text;
using GoalMaker.Core.Updates;
using GoalMaker.Infrastructure.Updates;

namespace GoalMaker.Core.Tests;

/// <summary>The GitHub Releases channel over a fake server: the right addresses, and nothing else (ADR 0010).</summary>
public sealed class GitHubReleaseChannelTests : IDisposable
{
    private const string Repository = "https://github.com/lukr-99/GoalMaker";
    private readonly string updates = Path.Combine(Path.GetTempPath(), "goalmaker-tests", Guid.NewGuid().ToString("N"), "updates");
    private readonly FakeServer server = new();
    private readonly HttpClient http;

    public GitHubReleaseChannelTests()
    {
        http = new HttpClient(server);
    }

    [Fact]
    public async Task TheLatestManifestAndItsSignatureComeFromTheLatestRelease()
    {
        var manifest = Encoding.UTF8.GetBytes("{\"schema\":1}");
        server.Files[Repository + "/releases/latest/download/manifest.json"] = manifest;
        server.Files[Repository + "/releases/latest/download/manifest.sig"] = Encoding.ASCII.GetBytes("c2lnbmF0dXJl\n");

        var snapshot = await Channel().FetchLatestAsync(TestContext.Current.CancellationToken);

        Assert.Equal(manifest, snapshot.ManifestBytes.ToArray());
        Assert.Equal("c2lnbmF0dXJl", snapshot.SignatureBase64);
        Assert.Equal(
            [Repository + "/releases/latest/download/manifest.json", Repository + "/releases/latest/download/manifest.sig"],
            server.Requested);
    }

    [Fact]
    public async Task AMissingManifestThrows()
    {
        await Assert.ThrowsAsync<HttpRequestException>(() => Channel().FetchLatestAsync(TestContext.Current.CancellationToken));
    }

    [Fact]
    public async Task AnArtifactIsStreamedFromItsReleaseWithItsSizeAndHash()
    {
        var bytes = new byte[200_000];
        new Random(7).NextBytes(bytes);
        server.Files[Repository + "/releases/download/v1.2.0/GoalMaker-1.2.0-setup.exe"] = bytes;
        Directory.CreateDirectory(updates);
        File.WriteAllText(Path.Combine(updates, "stale.exe"), "old");
        var read = new List<long>();

        var downloaded = await Channel().DownloadAsync(
            "1.2.0/GoalMaker-1.2.0-setup.exe", new SynchronousProgress(read.Add), TestContext.Current.CancellationToken);

        Assert.Equal([Repository + "/releases/download/v1.2.0/GoalMaker-1.2.0-setup.exe"], server.Requested);
        Assert.Equal(Path.Combine(updates, "GoalMaker-1.2.0-setup.exe"), downloaded.LocalPath);
        Assert.Equal(bytes.LongLength, downloaded.Size);
        Assert.Equal(Convert.ToHexStringLower(SHA256.HashData(bytes)), downloaded.Sha256);
        Assert.Equal(bytes, File.ReadAllBytes(downloaded.LocalPath));
        Assert.False(File.Exists(Path.Combine(updates, "stale.exe")));
        Assert.Equal(bytes.LongLength, read[^1]);
    }

    [Theory]
    [InlineData("../1.2.0/GoalMaker-1.2.0-setup.exe")]
    [InlineData("1.2.0/windows/GoalMaker-1.2.0-setup.exe")]
    [InlineData("1.2.0-dev/GoalMaker-setup.exe")]
    [InlineData("1.2.0/..")]
    public async Task APathOutsideAReleaseIsRefusedWithoutARequest(string path)
    {
        await Assert.ThrowsAsync<InvalidOperationException>(
            () => Channel().DownloadAsync(path, null, TestContext.Current.CancellationToken));

        Assert.Empty(server.Requested);
    }

    [Fact]
    public async Task AMissingArtifactThrows()
    {
        await Assert.ThrowsAsync<HttpRequestException>(
            () => Channel().DownloadAsync("1.2.0/GoalMaker-1.2.0-setup.exe", null, TestContext.Current.CancellationToken));

        Assert.Single(server.Requested);
    }

    public void Dispose()
    {
        http.Dispose();
        var root = Path.GetDirectoryName(updates)!;
        if (Directory.Exists(root))
        {
            Directory.Delete(root, recursive: true);
        }
    }

    private GitHubReleaseChannel Channel() => new(http, new ReleaseChannelAddress(Repository + "/"), updates);

    /// <summary>Answers with the file at the requested address, or 404.</summary>
    private sealed class FakeServer : HttpMessageHandler
    {
        public Dictionary<string, byte[]> Files { get; } = new(StringComparer.Ordinal);

        public List<string> Requested { get; } = [];

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            var url = request.RequestUri!.AbsoluteUri;
            Requested.Add(url);
            return Task.FromResult(Files.TryGetValue(url, out var bytes)
                ? new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(bytes) }
                : new HttpResponseMessage(HttpStatusCode.NotFound));
        }
    }

    /// <summary>Reports on the caller's thread, so the test sees every value before it asserts.</summary>
    private sealed class SynchronousProgress(Action<long> report) : IProgress<long>
    {
        public void Report(long value) => report(value);
    }
}
