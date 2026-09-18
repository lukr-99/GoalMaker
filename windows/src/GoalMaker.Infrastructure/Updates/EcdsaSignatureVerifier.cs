using System.Security.Cryptography;
using GoalMaker.Core.Updates;

namespace GoalMaker.Infrastructure.Updates;

/// <summary>
/// ECDSA P-256 with SHA-256 over the exact manifest bytes; the signature is DER, base64-encoded
/// (ADR 0004). Uses only the platform crypto.
/// </summary>
public sealed class EcdsaSignatureVerifier : ISignatureVerifier, IDisposable
{
    private readonly ECDsa key;

    public EcdsaSignatureVerifier(string publicKeyBase64)
    {
        key = ECDsa.Create();
        key.ImportSubjectPublicKeyInfo(Convert.FromBase64String(publicKeyBase64), out _);
    }

    public bool Verify(ReadOnlySpan<byte> data, string signatureBase64)
    {
        byte[] signature;
        try
        {
            signature = Convert.FromBase64String(signatureBase64.Trim());
        }
        catch (FormatException)
        {
            return false;
        }

        if (signature.Length == 0)
        {
            return false;
        }

        try
        {
            return key.VerifyData(data, signature, HashAlgorithmName.SHA256, DSASignatureFormat.Rfc3279DerSequence);
        }
        catch (CryptographicException)
        {
            return false;
        }
    }

    public void Dispose() => key.Dispose();
}
