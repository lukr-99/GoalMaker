namespace GoalMaker.Core.Updates;

/// <summary>What the update channel currently publishes, unverified.</summary>
public sealed record ChannelSnapshot(ReadOnlyMemory<byte> ManifestBytes, string SignatureBase64);
