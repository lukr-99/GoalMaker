using System.Text.Json;
using GoalMaker.Core.Updates;

namespace GoalMaker.Core.Tests;

/// <summary>contracts/vectors/release-channel.json, the same file the Android tests read (ADR 0010).</summary>
public sealed class ReleaseChannelContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/release-channel.json").RootElement;

    [Fact]
    public void EveryChannelHasItsManifestSignatureAndReleasesPage()
    {
        var channels = vectors.GetProperty("channels").EnumerateArray().ToList();
        Assert.NotEmpty(channels);
        foreach (var channel in channels)
        {
            var address = new ReleaseChannelAddress(channel.GetProperty("updateUrl").GetString()!);
            Assert.Equal(channel.GetProperty("manifest").GetString(), address.Manifest);
            Assert.Equal(channel.GetProperty("signature").GetString(), address.Signature);
            Assert.Equal(channel.GetProperty("releasesPage").GetString(), address.ReleasesPage);
        }
    }

    [Fact]
    public void OnlyAVersionFolderAndOneFileNameHaveAnAddress()
    {
        var address = new ReleaseChannelAddress(vectors.GetProperty("artifactUpdateUrl").GetString()!);
        var artifacts = vectors.GetProperty("artifacts").EnumerateArray().ToList();
        Assert.Contains(artifacts, artifact => artifact.GetProperty("url").ValueKind == JsonValueKind.Null);
        foreach (var artifact in artifacts)
        {
            var path = artifact.GetProperty("path").GetString()!;
            var expected = artifact.GetProperty("url");
            var actual = address.Artifact(path);
            Assert.True(
                expected.ValueKind == JsonValueKind.Null ? actual is null : actual == expected.GetString(),
                $"'{path}' mapped to '{actual}'");
        }
    }
}
