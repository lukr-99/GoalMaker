using GoalMaker.Core.Updates;

namespace GoalMaker.Infrastructure.Updates;

/// <summary>Used when no update address is built in: there is nowhere to fetch a release from.</summary>
public sealed class NotConfiguredReleaseChannel : IReleaseChannel
{
    public Task<ChannelSnapshot> FetchLatestAsync(CancellationToken cancellationToken) =>
        throw new InvalidOperationException("This build has no update channel.");

    public Task<DownloadedArtifact> DownloadAsync(string path, IProgress<long>? progress, CancellationToken cancellationToken) =>
        throw new InvalidOperationException("This build has no update channel.");
}
