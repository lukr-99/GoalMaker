using GoalMaker.Core.Updates;

namespace GoalMaker.Infrastructure.Updates;

/// <summary>Used when no public key is built in: nothing verifies, so nothing installs.</summary>
public sealed class NotConfiguredSignatureVerifier : ISignatureVerifier
{
    public bool Verify(ReadOnlySpan<byte> data, string signatureBase64) => false;
}
