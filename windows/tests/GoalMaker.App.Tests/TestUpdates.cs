using System.Net.Http;
using System.Text;
using GoalMaker.Core.Updates;

namespace GoalMaker.App.Tests;

/// <summary>
/// An update channel the test controls: it holds a signed 1.1.0 for an installed 1.0.0, nothing it
/// can reach, or a download that fails its checksum. The signature "good" is the trusted one.
/// </summary>
internal sealed class TestUpdates : IReleaseChannel, IUpdateInstaller
{
    private static readonly string Hash = new('a', 64);

    public TestUpdates(string installed = "1.0.0")
    {
        Service = new UpdateService(installed, ReleasePlatform.Windows, true, this, new ReleaseVerifier(new Signatures()), this);
    }

    public UpdateService Service { get; }

    /// <summary>Whether the channel has a release to give; false means offline.</summary>
    public bool Reachable { get; set; } = true;

    /// <summary>The version the channel holds; the installed one makes a check find none.</summary>
    public string Latest { get; set; } = "1.1.0";

    /// <summary>Whether a download comes back with the wrong hash.</summary>
    public bool Corrupt { get; set; }

    public List<string> Launched { get; } = [];

    public Task<ChannelSnapshot> FetchLatestAsync(CancellationToken cancellationToken) =>
        Reachable ? Task.FromResult(new ChannelSnapshot(Manifest(Latest), "good")) : throw new HttpRequestException("offline");

    public Task<DownloadedArtifact> DownloadAsync(string path, IProgress<long>? progress, CancellationToken cancellationToken) =>
        Task.FromResult(new DownloadedArtifact(@"C:\updates\setup.exe", 100, Corrupt ? new string('b', 64) : Hash));

    public void Launch(string localPath) => Launched.Add(localPath);

    private static byte[] Manifest(string version) => Encoding.UTF8.GetBytes(
        "{\"schema\":1,\"version\":\"" + version + "\",\"publishedAt\":\"2026-10-01T12:00:00Z\"," +
        "\"artifacts\":[{\"platform\":\"windows\",\"path\":\"" + version + "/GoalMaker-" + version + "-setup.exe\",\"size\":100,\"sha256\":\"" + Hash + "\"}]}");

    private sealed class Signatures : ISignatureVerifier
    {
        public bool Verify(ReadOnlySpan<byte> data, string signatureBase64) => signatureBase64 == "good";
    }
}
