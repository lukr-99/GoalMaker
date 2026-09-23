using System.Net.Http;
using System.Security.Cryptography;
using System.Text;
using GoalMaker.Core.Updates;

namespace GoalMaker.Infrastructure.Updates;

/// <summary>
/// Reads the update channel from public GitHub Releases, with no sign-in (ADR 0010). The bytes are
/// trusted only through the signed manifest; this class just fetches them. GitHub answers release
/// downloads with a redirect to its file host, which the default handler follows.
/// </summary>
public sealed class GitHubReleaseChannel(HttpClient http, ReleaseChannelAddress address, string updatesDirectory) : IReleaseChannel
{
    private const int BufferSize = 81_920;

    public async Task<ChannelSnapshot> FetchLatestAsync(CancellationToken cancellationToken)
    {
        var manifest = await http.GetByteArrayAsync(address.Manifest, cancellationToken).ConfigureAwait(false);
        var signature = await http.GetByteArrayAsync(address.Signature, cancellationToken).ConfigureAwait(false);
        return new ChannelSnapshot(manifest, Encoding.ASCII.GetString(signature).Trim());
    }

    public async Task<DownloadedArtifact> DownloadAsync(string path, IProgress<long>? progress, CancellationToken cancellationToken)
    {
        var url = address.Artifact(path)
            ?? throw new InvalidOperationException($"The release file '{path}' is not where a release keeps its files.");

        if (Directory.Exists(updatesDirectory))
        {
            Directory.Delete(updatesDirectory, recursive: true);
        }

        Directory.CreateDirectory(updatesDirectory);
        var target = Path.Combine(updatesDirectory, Path.GetFileName(path));

        using var response = await http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, cancellationToken).ConfigureAwait(false);
        response.EnsureSuccessStatusCode();
        using var hash = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
        long total = 0;
        var source = await response.Content.ReadAsStreamAsync(cancellationToken).ConfigureAwait(false);
        await using (source.ConfigureAwait(false))
        {
            var file = new FileStream(target, FileMode.CreateNew, FileAccess.Write, FileShare.None, BufferSize, useAsync: true);
            await using (file.ConfigureAwait(false))
            {
                var buffer = new byte[BufferSize];
                int read;
                while ((read = await source.ReadAsync(buffer, cancellationToken).ConfigureAwait(false)) > 0)
                {
                    hash.AppendData(buffer, 0, read);
                    await file.WriteAsync(buffer.AsMemory(0, read), cancellationToken).ConfigureAwait(false);
                    total += read;
                    progress?.Report(total);
                }
            }
        }

        return new DownloadedArtifact(target, total, Convert.ToHexStringLower(hash.GetHashAndReset()));
    }
}
