using System.Text.Json;
using GoalMaker.Core.Notes;

namespace GoalMaker.Core.Tests.Notes;

/// <summary>contracts/vectors/markdown.json, the same file the Android tests read.</summary>
public sealed class LightMarkdownContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/markdown.json").RootElement;

    [Fact]
    public void EveryNote()
    {
        foreach (var testCase in vectors.GetProperty("cases").EnumerateArray())
        {
            var expected = testCase.GetProperty("blocks").EnumerateArray().Select(block => Describe(
                block.GetProperty("bullet").GetBoolean(),
                block.GetProperty("spans").EnumerateArray().Select(span => new MarkdownSpan(
                    span.GetProperty("text").GetString()!,
                    span.TryGetProperty("bold", out var bold) && bold.GetBoolean(),
                    span.TryGetProperty("italic", out var italic) && italic.GetBoolean(),
                    span.TryGetProperty("link", out var link) ? link.GetString() : null)))).ToList();
            var actual = LightMarkdown.Parse(testCase.GetProperty("text").GetString()!).Select(block => Describe(block.Bullet, block.Spans)).ToList();

            Assert.True(expected.SequenceEqual(actual), $"{testCase.GetProperty("name").GetString()}:\n  expected {string.Join(" | ", expected)}\n  got      {string.Join(" | ", actual)}");
        }
    }

    // Records with a list inside don't compare by value, so each block is compared as text.
    private static string Describe(bool bullet, IEnumerable<MarkdownSpan> spans) =>
        (bullet ? "- " : string.Empty) + string.Join(string.Empty, spans.Select(span => span.ToString()));
}
