namespace GoalMaker.Core.Updates;

/// <summary>Reads the update channel (the public GitHub Releases, ADR 0010). Throws on network or access errors.</summary>
public interface IReleaseChannel
{
    /// <summary>The latest manifest's exact bytes and its detached base64 signature.</summary>
    Task<ChannelSnapshot> FetchLatestAsync(CancellationToken cancellationToken);

    /// <summary>Downloads <paramref name="path"/> to a private file and returns it.</summary>
    Task<DownloadedArtifact> DownloadAsync(string path, IProgress<long>? progress, CancellationToken cancellationToken);
}
