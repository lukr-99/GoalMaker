using System.Text.Json;
using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>contracts/vectors/tags.json, the same file the Android tests and the connector read.</summary>
public sealed class TagRulesContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/tags.json").RootElement;

    [Fact]
    public void WhatATagPickerOffers()
    {
        foreach (var testCase in vectors.GetProperty("offered").EnumerateArray())
        {
            var chosen = testCase.GetProperty("chosen");
            var offered = TagRules.Offered(
                Tags(testCase),
                Ids(testCase, "areas").ToHashSet(StringComparer.Ordinal),
                Projects(testCase),
                Optional(chosen, "area"),
                Optional(chosen, "project"));
            Assert.True(Ids(testCase, "expect").SequenceEqual(offered.Select(tag => tag.Id)), Name(testCase));
        }
    }

    [Fact]
    public void HowSettingsGroupsTheTags()
    {
        foreach (var testCase in vectors.GetProperty("groups").EnumerateArray())
        {
            var groups = TagRules.Groups(Tags(testCase), Ids(testCase, "areas"), [.. Projects(testCase).Keys]);
            var expect = testCase.GetProperty("expect").EnumerateArray()
                .Select(group => $"{Optional(group, "place") ?? "-"}: {string.Join(",", group.GetProperty("tags").EnumerateArray().Select(id => id.GetString()))}");
            var actual = groups.Select(group => $"{Label(group.Place)}: {string.Join(",", group.Tags.Select(tag => tag.Id))}");
            Assert.True(expect.SequenceEqual(actual), Name(testCase));
        }
    }

    private static string Label(TagPlace? place) => place switch
    {
        { AreaId: { } area } => $"area:{area}",
        { ProjectId: { } project } => $"project:{project}",
        _ => "-",
    };

    private static string Name(JsonElement testCase) => testCase.GetProperty("name").GetString()!;

    private static string? Optional(JsonElement element, string name) =>
        element.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String ? value.GetString() : null;

    private static List<string> Ids(JsonElement element, string name) =>
        [.. element.GetProperty(name).EnumerateArray().Select(id => id.GetString()!)];

    private static List<TagItem> Tags(JsonElement testCase) =>
        [.. testCase.GetProperty("tags").EnumerateArray().Select(tag => new TagItem(
            tag.GetProperty("id").GetString()!, tag.GetProperty("id").GetString()!, Optional(tag, "area"), Optional(tag, "project")))];

    private static Dictionary<string, string?> Projects(JsonElement testCase) =>
        testCase.GetProperty("projects").EnumerateObject().ToDictionary(
            project => project.Name,
            project => project.Value.ValueKind == JsonValueKind.String ? project.Value.GetString() : null,
            StringComparer.Ordinal);
}
