using System.ComponentModel;
using GoalMaker.App.Shell;

namespace GoalMaker.App.Tests;

/// <summary>A link that will not open tells the owner instead of crashing the app (GM-103).</summary>
public sealed class LinkOpenerTests
{
    [Fact]
    public void AnOpenedLinkSaysNothing()
    {
        var opened = new List<string>();
        var told = new List<string>();

        var result = LinkOpener.Open("https://example.com/a", opened.Add, told.Add);

        Assert.True(result);
        Assert.Equal(["https://example.com/a"], opened);
        Assert.Empty(told);
    }

    [Theory]
    [MemberData(nameof(Failures))]
    public void AFailureToOpenTellsTheOwnerTheLinkAndDoesNotThrow(Exception failure)
    {
        var told = new List<string>();

        var result = LinkOpener.Open("https://example.com/b", _ => throw failure, told.Add);

        Assert.False(result);
        Assert.Equal(["https://example.com/b"], told);
    }

    [Fact]
    public void AnUnexpectedBugStillSurfaces()
    {
        Assert.Throws<NullReferenceException>(() =>
            LinkOpener.Open("https://example.com/c", _ => throw new NullReferenceException(), _ => { }));
    }

    public static TheoryData<Exception> Failures() => new()
    {
        new Win32Exception(1155, "No application is associated with the file."),
        new InvalidOperationException("No shell."),
        new FileNotFoundException("Not found."),
    };
}
