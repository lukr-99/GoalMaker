using System.Text.Json;
using GoalMaker.Core.Updates;
using GoalMaker.Infrastructure.Updates;

namespace GoalMaker.Core.Tests;

/// <summary>contracts/vectors/release-manifest.json through the real verifier and parser.</summary>
public sealed class ReleaseManifestContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/release-manifest.json").RootElement;

    [Fact]
    public void EveryCaseHasTheExpectedOutcome()
    {
        using var key = new EcdsaSignatureVerifier(vectors.GetProperty("trustedPublicKey").GetString()!);
        var verifier = new ReleaseVerifier(key);
        var cases = vectors.GetProperty("cases").EnumerateArray().ToList();
        Assert.True(cases.Count >= 10);

        foreach (var testCase in cases)
        {
            var name = testCase.GetProperty("name").GetString();
            var snapshot = new ChannelSnapshot(
                Convert.FromBase64String(testCase.GetProperty("manifestBase64").GetString()!),
                testCase.GetProperty("signatureBase64").GetString()!);
            var result = verifier.Check(snapshot);
            switch (testCase.GetProperty("outcome").GetString())
            {
                case "bad-signature":
                    Assert.True(result is ManifestCheck.BadSignature, $"{name}: expected bad-signature, got {result}");
                    break;
                case "bad-manifest":
                    Assert.True(result is ManifestCheck.BadManifest, $"{name}: expected bad-manifest, got {result}");
                    break;
                case "valid":
                    var manifest = Assert.IsType<ManifestCheck.Valid>(result).Manifest;
                    Assert.Equal(testCase.GetProperty("version").GetString(), manifest.Version.ToString());
                    var code = testCase.GetProperty("androidVersionCode");
                    Assert.Equal(code.ValueKind == JsonValueKind.Null ? null : code.GetInt32(), manifest.AndroidVersionCode);
                    var expected = testCase.GetProperty("artifacts").EnumerateArray().ToList();
                    Assert.Equal(expected.Count, manifest.Artifacts.Count);
                    foreach (var (want, got) in expected.Zip(manifest.Artifacts))
                    {
                        Assert.Equal(want.GetProperty("platform").GetString(), got.Platform.ToString().ToLowerInvariant());
                        Assert.Equal(want.GetProperty("path").GetString(), got.Path);
                        Assert.Equal(want.GetProperty("size").GetInt64(), got.Size);
                        Assert.Equal(want.GetProperty("sha256").GetString(), got.Sha256);
                    }

                    break;
                default:
                    Assert.Fail($"{name}: unknown outcome");
                    break;
            }
        }
    }

    [Fact]
    public void TheVectorFileDeclaresItsSchema() => Assert.Equal(1, vectors.GetProperty("schema").GetInt32());
}
