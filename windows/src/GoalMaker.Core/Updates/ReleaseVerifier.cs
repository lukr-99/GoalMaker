namespace GoalMaker.Core.Updates;

/// <summary>
/// Signature first, content second: unsigned bytes are never parsed. The combined behavior is the
/// contract in contracts/vectors/release-manifest.json.
/// </summary>
public sealed class ReleaseVerifier(ISignatureVerifier signatures)
{
    public ManifestCheck Check(ChannelSnapshot snapshot) =>
        signatures.Verify(snapshot.ManifestBytes.Span, snapshot.SignatureBase64)
            ? ReleaseManifestParser.Parse(snapshot.ManifestBytes)
            : new ManifestCheck.BadSignature();
}
