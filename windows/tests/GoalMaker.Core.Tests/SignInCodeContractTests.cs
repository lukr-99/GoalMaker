using System.Text.Json;
using GoalMaker.Core.Account;

namespace GoalMaker.Core.Tests;

/// <summary>contracts/vectors/sign-in-code.json, the same file the Android tests read.</summary>
public sealed class SignInCodeContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/sign-in-code.json").RootElement;

    [Fact]
    public void EveryTypedCode()
    {
        foreach (var testCase in vectors.GetProperty("parse").EnumerateArray())
        {
            var text = testCase.GetProperty("text").GetString()!;
            Assert.True(testCase.GetProperty("expect").GetString() == SignInCode.Parse(text)?.Value, text);
        }
    }

    [Fact]
    public void EveryCodeFoundInCopiedText()
    {
        foreach (var testCase in vectors.GetProperty("find").EnumerateArray())
        {
            Assert.True(
                testCase.GetProperty("expect").GetString() == SignInCode.Find(testCase.GetProperty("text").GetString()!)?.Value,
                testCase.GetProperty("name").GetString());
        }
    }
}
