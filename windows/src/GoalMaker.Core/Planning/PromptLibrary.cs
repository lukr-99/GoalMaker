using System.Text.Json;

namespace GoalMaker.Core.Planning;

/// <summary>
/// The review prompts the app ships (contracts/content/prompts.json), in the file's order: the rotation
/// walks the categories and takes from the prompts (docs/reviews.md).
/// </summary>
public sealed class PromptLibrary
{
    private readonly Dictionary<string, ReviewPrompt> byId;

    public PromptLibrary(int version, IReadOnlyList<string> categories, IReadOnlyList<ReviewPrompt> prompts)
    {
        Version = version;
        Categories = categories;
        Prompts = prompts;
        byId = prompts.ToDictionary(prompt => prompt.Id, StringComparer.Ordinal);
    }

    public int Version { get; }

    public IReadOnlyList<string> Categories { get; }

    public IReadOnlyList<ReviewPrompt> Prompts { get; }

    public ReviewPrompt? this[string id] => byId.TryGetValue(id, out var prompt) ? prompt : null;

    public static PromptLibrary Parse(string text)
    {
        using var document = JsonDocument.Parse(text);
        return Read(document.RootElement);
    }

    public static PromptLibrary Load(Stream stream)
    {
        using var document = JsonDocument.Parse(stream);
        return Read(document.RootElement);
    }

    private static PromptLibrary Read(JsonElement root) => new(
        root.GetProperty("version").GetInt32(),
        [.. root.GetProperty("categories").EnumerateArray().Select(category => category.GetProperty("id").GetString()!)],
        [.. root.GetProperty("prompts").EnumerateArray().Select(prompt => new ReviewPrompt(
            prompt.GetProperty("id").GetString()!,
            prompt.GetProperty("category").GetString()!,
            prompt.GetProperty("reviews").EnumerateArray().Select(kind => kind.GetString()!).ToHashSet(StringComparer.Ordinal),
            prompt.GetProperty("text").GetString()!,
            prompt.TryGetProperty("trigger", out var trigger) ? trigger.GetString() : null))]);
}
