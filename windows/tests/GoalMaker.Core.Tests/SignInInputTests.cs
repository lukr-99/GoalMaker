using GoalMaker.Core.Account;

namespace GoalMaker.Core.Tests;

public sealed class SignInInputTests
{
    [Theory]
    [InlineData("  me@example.com ", "me@example.com")]
    [InlineData("me@example", null)]
    [InlineData("not an email", null)]
    [InlineData("", null)]
    [InlineData("me@example.com\n", "me@example.com")]
    public void EmailsAreTrimmedAndLooselyValidated(string input, string? expected) =>
        Assert.Equal(expected, EmailAddress.Parse(input)?.Value);

    [Theory]
    [InlineData("123 456", "123456")]
    [InlineData("12345", null)]
    [InlineData("1234567", null)]
    [InlineData("12a456", null)]
    [InlineData("١٢٣٤٥٦", null)]
    public void CodesAreSixAsciiDigitsSpacesAllowed(string input, string? expected) =>
        Assert.Equal(expected, SignInCode.Parse(input)?.Value);
}
