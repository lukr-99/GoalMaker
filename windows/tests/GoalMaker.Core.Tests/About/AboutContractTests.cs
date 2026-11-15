using System.Text.Json;
using GoalMaker.Core.About;

namespace GoalMaker.Core.Tests.About;

/// <summary>contracts/vectors/about.json, the same file the phone's AboutContractTest reads.</summary>
public sealed class AboutContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/about.json").RootElement;

    [Fact]
    public void TheAuthorIsTheContracts()
    {
        Assert.Equal(AuthorLinks.Name, vectors.GetProperty("author").GetString());
    }

    [Fact]
    public void TheLinksAreTheContractsInItsOrder()
    {
        var expected = vectors.GetProperty("links").EnumerateArray()
            .Select(link => new AuthorLink(
                link.GetProperty("id").GetString()!,
                link.GetProperty("url").GetString()!,
                link.GetProperty("handle").GetString()!))
            .ToList();

        Assert.Equal(expected, AuthorLinks.All);
    }

    [Fact]
    public void EveryLinkIsHttps()
    {
        Assert.All(AuthorLinks.All, link => Assert.StartsWith("https://", link.Url, StringComparison.Ordinal));
    }

    [Fact]
    public void EveryMonogramCase()
    {
        foreach (var check in vectors.GetProperty("monogram").EnumerateArray())
        {
            var name = check.GetProperty("name").GetString()!;
            Assert.True(check.GetProperty("expect").GetString() == Monogram.Of(name), name);
        }
    }
}
