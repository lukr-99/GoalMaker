using System.Security.Cryptography;
using GoalMaker.Core.Updates;

namespace GoalMaker.Infrastructure.Updates;

/// <summary>Reads the private releases bucket with the signed-in user's session (ADR 0004).</summary>
public sealed class SupabaseReleaseChannel(Supabase.Client client, string updatesDirectory) : IReleaseChannel
{
    public const string Bucket = "releases";
    private const string ManifestPath = "latest/manifest.json";
    private const string SignaturePath = "latest/manifest.sig";

    public async Task<ChannelSnapshot> FetchLatestAsync(CancellationToken cancellationToken)
    {
        var bucket = client.Storage.From(Bucket);
        var manifest = await bucket.Download(ManifestPath, (EventHandler<float>?)null, cancellationToken).ConfigureAwait(false);
        var signature = await bucket.Download(SignaturePath, (EventHandler<float>?)null, cancellationToken).ConfigureAwait(false);
        return new ChannelSnapshot(manifest, System.Text.Encoding.ASCII.GetString(signature).Trim());
    }

    public async Task<DownloadedArtifact> DownloadAsync(string path, IProgress<long>? progress, CancellationToken cancellationToken)
    {
        if (Directory.Exists(updatesDirectory))
        {
            Directory.Delete(updatesDirectory, recursive: true);
        }

        Directory.CreateDirectory(updatesDirectory);
        var target = Path.Combine(updatesDirectory, Path.GetFileName(path));
        var bytes = await client.Storage.From(Bucket).Download(path, (EventHandler<float>?)null, cancellationToken).ConfigureAwait(false);
        await File.WriteAllBytesAsync(target, bytes, cancellationToken).ConfigureAwait(false);
        progress?.Report(bytes.LongLength);
        return new DownloadedArtifact(target, bytes.LongLength, Convert.ToHexStringLower(SHA256.HashData(bytes)));
    }
}
