using GoalMaker.Core.Versioning;

namespace GoalMaker.Core.Updates;

/// <summary>
/// Release discovery → signature and content check → version policy → verified download → installer
/// launch, each behind its own seam (CodePrint updater rule). Never touches user data.
/// </summary>
public sealed class UpdateService(
    string installedVersion,
    ReleasePlatform platform,
    bool channelConfigured,
    IReleaseChannel channel,
    ReleaseVerifier verifier,
    IUpdateInstaller installer)
{
    /// <summary>
    /// The update the last check found, or null: the mark on the way to Settings. Every check that
    /// gets an answer replaces it, so it goes when one finds none; a check that fails leaves it, and a
    /// new version starts without it.
    /// </summary>
    public UpdateCheckResult.Available? Waiting { get; private set; }

    /// <summary><see cref="Waiting"/> changed. May be raised off the UI thread.</summary>
    public event EventHandler? WaitingChanged;

    /// <summary>
    /// Whether a check can reach anything at all: the build has a channel and is a release. A dev
    /// build or one without the key answers every check without the network.
    /// </summary>
    public bool CanCheck =>
        channelConfigured && SemanticVersion.Parse(installedVersion) is { IsDevelopmentBuild: false };

    public async Task<UpdateCheckResult> CheckAsync(CancellationToken cancellationToken)
    {
        var result = await FindAsync(cancellationToken).ConfigureAwait(false);
        // A check that could not reach the channel (offline, GitHub down) leaves the waiting update as it was.
        if (result is UpdateCheckResult.Failed)
        {
            return result;
        }

        var waiting = result as UpdateCheckResult.Available;
        if (waiting != Waiting)
        {
            Waiting = waiting;
            WaitingChanged?.Invoke(this, EventArgs.Empty);
        }

        return result;
    }

    private async Task<UpdateCheckResult> FindAsync(CancellationToken cancellationToken)
    {
        if (!channelConfigured)
        {
            return new UpdateCheckResult.NotConfigured();
        }

        if (SemanticVersion.Parse(installedVersion) is not { IsDevelopmentBuild: false })
        {
            return new UpdateCheckResult.DevelopmentBuild();
        }

        ChannelSnapshot snapshot;
        try
        {
            snapshot = await channel.FetchLatestAsync(cancellationToken).ConfigureAwait(false);
        }
        catch (Exception error) when (error is not OperationCanceledException)
        {
            return new UpdateCheckResult.Failed(error.Message);
        }

        if (verifier.Check(snapshot) is not ManifestCheck.Valid valid)
        {
            return new UpdateCheckResult.Untrusted();
        }

        var manifest = valid.Manifest;
        var artifact = manifest.ArtifactFor(platform);
        return artifact is not null && UpdatePolicy.ShouldOffer(installedVersion, manifest.Version.ToString())
            ? new UpdateCheckResult.Available(manifest, artifact)
            : new UpdateCheckResult.UpToDate(manifest.Version.ToString());
    }

    public async Task<InstallResult> InstallAsync(
        UpdateCheckResult.Available update,
        IProgress<double>? progress,
        CancellationToken cancellationToken)
    {
        var expected = update.Artifact;
        var bytesProgress = progress is null
            ? null
            : new Progress<long>(read => progress.Report(Math.Clamp((double)read / expected.Size, 0, 1)));
        DownloadedArtifact downloaded;
        try
        {
            downloaded = await channel.DownloadAsync(expected.Path, bytesProgress, cancellationToken).ConfigureAwait(false);
        }
        catch (Exception error) when (error is not OperationCanceledException)
        {
            return new InstallResult.Failed(error.Message);
        }

        if (downloaded.Size != expected.Size
            || !string.Equals(downloaded.Sha256, expected.Sha256, StringComparison.OrdinalIgnoreCase))
        {
            return new InstallResult.DownloadCorrupted();
        }

        installer.Launch(downloaded.LocalPath);
        return new InstallResult.InstallerStarted();
    }
}
