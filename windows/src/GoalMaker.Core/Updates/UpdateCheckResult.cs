namespace GoalMaker.Core.Updates;

/// <summary>What "Check for updates" found.</summary>
public abstract record UpdateCheckResult
{
    private UpdateCheckResult()
    {
    }

    /// <summary>No trusted key is built in, so the channel can't be verified (docs/setup).</summary>
    public sealed record NotConfigured : UpdateCheckResult;

    /// <summary>Development builds never update themselves; install a release build instead.</summary>
    public sealed record DevelopmentBuild : UpdateCheckResult;

    public sealed record UpToDate(string Latest) : UpdateCheckResult;

    public sealed record Available(ReleaseManifest Manifest, ReleaseArtifact Artifact) : UpdateCheckResult;

    public sealed record Untrusted : UpdateCheckResult;

    public sealed record Failed(string Detail) : UpdateCheckResult;
}
