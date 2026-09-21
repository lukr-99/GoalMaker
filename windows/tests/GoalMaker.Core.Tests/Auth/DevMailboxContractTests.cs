using System.Text.Json;
using GoalMaker.Core.Auth;

namespace GoalMaker.Core.Tests.Auth;

/// <summary>contracts/vectors/dev-mailbox.json, the same file the Android tests read.</summary>
public sealed class DevMailboxContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/dev-mailbox.json").RootElement;

    [Fact]
    public void EveryBackendFindsItsMailboxOrNone()
    {
        foreach (var testCase in vectors.GetProperty("mailbox").EnumerateArray())
        {
            var name = testCase.GetProperty("name").GetString();
            var expected = testCase.GetProperty("expect");
            Assert.Equal(
                $"{name}: {(expected.ValueKind == JsonValueKind.Null ? null : expected.GetString())}",
                $"{name}: {DevMailbox.Of(testCase.GetProperty("backend").GetString())}");
        }
    }

    [Fact]
    public void EveryMessageGivesUpItsCodeOrNone()
    {
        foreach (var testCase in vectors.GetProperty("codes").EnumerateArray())
        {
            var name = testCase.GetProperty("name").GetString();
            var expected = testCase.GetProperty("expect");
            Assert.Equal(
                $"{name}: {(expected.ValueKind == JsonValueKind.Null ? null : expected.GetString())}",
                $"{name}: {DevMailbox.CodeIn(testCase.GetProperty("text").GetString())}");
        }
    }
}
