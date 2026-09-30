using System.Text.Json;

namespace GoalMaker.Core.Planning;

/// <summary>
/// The Tally categories and rules the app ships (contracts/content/tally-rules.json), in the file's
/// order: the owner's own rules come first, then these, and the first match wins (docs/tally.md).
/// </summary>
public sealed class TallyDefaults(IReadOnlyList<TallyCategory> categories, IReadOnlyList<TallyRule> rules)
{
    public IReadOnlyList<TallyCategory> Categories { get; } = categories;

    public IReadOnlyList<TallyRule> Rules { get; } = rules;

    public static TallyDefaults Parse(string text)
    {
        using var document = JsonDocument.Parse(text);
        return Read(document.RootElement);
    }

    public static TallyDefaults Load(Stream stream)
    {
        using var document = JsonDocument.Parse(stream);
        return Read(document.RootElement);
    }

    private static TallyDefaults Read(JsonElement root) => new(
        [.. root.GetProperty("categories").EnumerateArray().Select(category => new TallyCategory(
            category.GetProperty("id").GetString()!,
            category.GetProperty("name").GetString()!,
            category.GetProperty("color").GetString()!,
            category.TryGetProperty("emoji", out var emoji) ? emoji.GetString() : null))],
        [.. root.GetProperty("rules").EnumerateArray().Select(rule => new TallyRule(
            rule.GetProperty("match").GetString()!,
            rule.GetProperty("pattern").GetString()!,
            rule.GetProperty("platform").GetString()!,
            rule.GetProperty("category").GetString()!))]);
}
