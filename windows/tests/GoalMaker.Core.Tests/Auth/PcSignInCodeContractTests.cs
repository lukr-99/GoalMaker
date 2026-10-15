using System.Text.Json;
using GoalMaker.Core.Auth;

namespace GoalMaker.Core.Tests.Auth;

/// <summary>contracts/vectors/pc-sign-in.json, the same file the Android tests and the function read.</summary>
public sealed class PcSignInCodeContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/pc-sign-in.json").RootElement;

    [Fact]
    public void ThePrefixIsTheOneTheContractNames() =>
        Assert.Equal(PcSignInCode.Prefix, vectors.GetProperty("prefix").GetString());

    [Fact]
    public void EverySecretBuildsItsText()
    {
        foreach (var testCase in vectors.GetProperty("build").EnumerateArray())
        {
            Assert.Equal(
                $"{testCase.GetProperty("name").GetString()}: {testCase.GetProperty("expect").GetString()}",
                $"{testCase.GetProperty("name").GetString()}: {PcSignInCode.QrText(testCase.GetProperty("secret").GetString()!)}");
        }
    }

    [Fact]
    public void EveryTextGivesUpItsSecretOrNone()
    {
        foreach (var testCase in vectors.GetProperty("read").EnumerateArray())
        {
            var name = testCase.GetProperty("name").GetString();
            var expected = testCase.GetProperty("expect");
            Assert.Equal(
                $"{name}: {(expected.ValueKind == JsonValueKind.Null ? null : expected.GetString())}",
                $"{name}: {PcSignInCode.ScanSecretOf(testCase.GetProperty("text").GetString())}");
        }
    }
}
