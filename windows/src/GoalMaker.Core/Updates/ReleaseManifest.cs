using GoalMaker.Core.Versioning;

namespace GoalMaker.Core.Updates;

/// <summary>The latest release as described by a verified release manifest (contracts/schemas).</summary>
public sealed record ReleaseManifest(
    SemanticVersion Version,
    int? AndroidVersionCode,
    string PublishedAt,
    string? Notes,
    IReadOnlyList<ReleaseArtifact> Artifacts)
{
    public ReleaseArtifact? ArtifactFor(ReleasePlatform platform) =>
        Artifacts.FirstOrDefault(artifact => artifact.Platform == platform);
}
