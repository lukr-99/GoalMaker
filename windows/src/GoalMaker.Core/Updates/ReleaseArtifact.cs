namespace GoalMaker.Core.Updates;

/// <summary>One downloadable file of a release, located by its path in the manifest ("X.Y.Z/file").</summary>
public sealed record ReleaseArtifact(ReleasePlatform Platform, string Path, long Size, string Sha256);
