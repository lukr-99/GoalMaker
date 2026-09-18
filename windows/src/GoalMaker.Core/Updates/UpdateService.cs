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
    public async Task<UpdateCheckResult> CheckAsync(CancellationToken cancellationToken)
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
