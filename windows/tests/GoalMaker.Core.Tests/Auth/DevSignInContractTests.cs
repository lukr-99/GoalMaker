using System.Text.Json;
using GoalMaker.Core.Auth;

namespace GoalMaker.Core.Tests.Auth;

/// <summary>contracts/vectors/dev-sign-in.json, the same file the Android tests read.</summary>
public sealed class DevSignInContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/dev-sign-in.json").RootElement;

    [Fact]
    public void TheDevAccountIsTheOneTheContractNames() =>
        Assert.Equal(DevSignIn.Email, vectors.GetProperty("account").GetProperty("email").GetString());

    [Fact]
    public void EveryBackendFindsItsMailboxOrNone()
    {
        foreach (var testCase in vectors.GetProperty("mailbox").EnumerateArray())
        {
            var name = testCase.GetProperty("name").GetString();
            var expected = testCase.GetProperty("expect");
            Assert.Equal(
                $"{name}: {(expected.ValueKind == JsonValueKind.Null ? null : expected.GetString())}",
                $"{name}: {DevSignIn.MailboxOf(testCase.GetProperty("backend").GetString())}");
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
                $"{name}: {DevSignIn.CodeIn(testCase.GetProperty("text").GetString())}");
        }
    }
}
