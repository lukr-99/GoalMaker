using System.Globalization;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using GoalMaker.Core.Composer;

namespace GoalMaker.Core.Tests.Composer;

/// <summary>contracts/vectors/composer.json, the same file the Android tests read.</summary>
public sealed class ComposerParserContractTests
{
    private readonly JsonObject vectors = JsonNode.Parse(ContractFiles.Load("vectors/composer.json").RootElement.GetRawText())!.AsObject();

    [Fact]
    public void EveryComposerVector()
    {
        var defaults = vectors["defaults"]!.AsObject();
        var cases = vectors["cases"]!.AsArray().Select(node => node!.AsObject()).ToList();
        var failures = new List<string>();
        foreach (var testCase in cases)
        {
            var (input, spans) = Unmark((string)testCase["marked"]!);
            var now = DateTime.ParseExact((string)(testCase["now"] ?? defaults["now"])!, "yyyy-MM-dd'T'HH:mm", CultureInfo.InvariantCulture);
            var rollover = (int)(testCase["rolloverHour"] ?? defaults["rolloverHour"])!;
            var expected = (JsonObject)defaults["expect"]!.DeepClone();
            foreach (var (key, value) in testCase["expect"]!.AsObject())
            {
                expected[key] = value?.DeepClone();
            }

            var draft = ComposerParser.Parse(input, now, rollover);
            var actual = Describe(draft);
            if (!JsonNode.DeepEquals(actual, expected))
            {
                failures.Add($"{testCase["name"]}: expected {expected.ToJsonString()}\n    got      {actual.ToJsonString()}");
            }
            else if (!draft.Spans.SequenceEqual(spans))
            {
                failures.Add($"{testCase["name"]}: expected spans {string.Join(", ", spans)}\n    got            {string.Join(", ", draft.Spans)}");
            }
        }

        Assert.True(failures.Count == 0, $"{failures.Count} of {cases.Count} failed:\n{string.Join('\n', failures)}");
    }

    [Fact]
    public void TheMarkupReaderFindsOffsetsInThePlainLine()
    {
        var (input, spans) = Unmark("Buy [[date:tmrw]] now");
        Assert.Equal("Buy tmrw now", input);
        Assert.Equal([new ComposerSpan(SpanKind.Date, 4, 8)], spans);
    }

    private static JsonObject Describe(ComposerDraft draft) => new()
    {
        ["title"] = draft.Title,
        ["plannedDate"] = draft.PlannedDate?.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture),
        ["plannedTime"] = draft.PlannedTime?.ToString("HH:mm", CultureInfo.InvariantCulture),
        ["tags"] = new JsonArray([.. draft.Tags.Select(tag => (JsonNode?)tag)]),
        ["area"] = draft.Area,
        ["project"] = draft.Project,
        ["topPriority"] = draft.TopPriority,
        ["idea"] = draft.Idea,
        ["repeat"] = draft.Repeat,
        ["command"] = draft.Command is { } command
            ? new JsonObject { ["name"] = command.Name, ["argument"] = command.Argument, ["known"] = command.Known }
            : null,
    };

    /// <summary>"Buy [[date:tmrw]]" gives ("Buy tmrw", a Date span over "tmrw").</summary>
    private static (string Input, List<ComposerSpan> Spans) Unmark(string marked)
    {
        var input = new StringBuilder();
        var spans = new List<ComposerSpan>();
        var index = 0;
        while (index < marked.Length)
        {
            if (string.CompareOrdinal(marked, index, "[[", 0, 2) == 0)
            {
                var close = marked.IndexOf("]]", index, StringComparison.Ordinal);
                var body = marked[(index + 2)..close];
                var colon = body.IndexOf(':', StringComparison.Ordinal);
                var kind = Enum.Parse<SpanKind>(body[..colon], ignoreCase: true);
                var text = body[(colon + 1)..];
                spans.Add(new ComposerSpan(kind, input.Length, input.Length + text.Length));
                input.Append(text);
                index = close + 2;
            }
            else
            {
                input.Append(marked[index]);
                index++;
            }
        }

        return (input.ToString(), spans);
    }
}
