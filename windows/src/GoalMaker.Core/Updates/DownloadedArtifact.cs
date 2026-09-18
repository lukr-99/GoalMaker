namespace GoalMaker.Core.Updates;

/// <summary>A file written to private storage, with the size and SHA-256 measured while writing it.</summary>
public sealed record DownloadedArtifact(string LocalPath, long Size, string Sha256);
