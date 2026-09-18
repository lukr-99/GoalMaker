using GoalMaker.Core.Updates;
using GoalMaker.Core.Versioning;

namespace GoalMaker.Core.Tests;

/// <summary>contracts/vectors/semantic-version.json, the same file the Android tests read.</summary>
public sealed class SemanticVersionContractTests
{
    private readonly System.Text.Json.JsonElement vectors = ContractFiles.Load("vectors/semantic-version.json").RootElement;

    [Fact]
    public void ParsesExactlyTheValidVersions()
    {
        var cases = vectors.GetProperty("parse").EnumerateArray().ToList();
        Assert.NotEmpty(cases);
        foreach (var testCase in cases)
        {
            var input = testCase.GetProperty("input").GetString()!;
            var parsed = SemanticVersion.Parse(input);
            if (!testCase.GetProperty("valid").GetBoolean())
            {
                Assert.True(parsed is null, $"'{input}' must be rejected");
                continue;
            }

            Assert.True(parsed is not null, $"'{input}' must parse");
            Assert.Equal(testCase.GetProperty("major").GetInt64(), parsed.Major);
            Assert.Equal(testCase.GetProperty("minor").GetInt64(), parsed.Minor);
            Assert.Equal(testCase.GetProperty("patch").GetInt64(), parsed.Patch);
            Assert.Equal(
                testCase.GetProperty("prerelease").EnumerateArray().Select(item => item.GetString()!).ToList(),
                parsed.Prerelease.ToList());
        }
    }

    [Fact]
    public void OrdersVersionsBySemanticPrecedence()
    {
        foreach (var testCase in vectors.GetProperty("compare").EnumerateArray())
        {
            var a = SemanticVersion.Parse(testCase.GetProperty("a").GetString()!)!;
            var b = SemanticVersion.Parse(testCase.GetProperty("b").GetString()!)!;
            var expected = testCase.GetProperty("result").GetInt32();
            Assert.True(Math.Sign(a.CompareTo(b)) == expected, $"{a} vs {b}");
            Assert.True(Math.Sign(b.CompareTo(a)) == -expected, $"{b} vs {a}");
        }
    }

    [Fact]
    public void OffersOnlyNewerStableReleasesToReleaseBuilds()
    {
        foreach (var testCase in vectors.GetProperty("offerUpdate").EnumerateArray())
        {
            var installed = testCase.GetProperty("installed").GetString()!;
            var available = testCase.GetProperty("available").GetString()!;
            Assert.True(
                testCase.GetProperty("offer").GetBoolean() == UpdatePolicy.ShouldOffer(installed, available),
                $"{installed} -> {available}");
        }
    }
}
