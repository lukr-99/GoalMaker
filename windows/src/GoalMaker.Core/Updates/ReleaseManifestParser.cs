using System.Text.Json;
using System.Text.RegularExpressions;
using GoalMaker.Core.Versioning;

namespace GoalMaker.Core.Updates;

/// <summary>
/// Parses and validates release manifest bytes that already passed the signature check. Unknown
/// properties are ignored; every rule is covered by contracts/vectors/release-manifest.json.
/// </summary>
public static partial class ReleaseManifestParser
{
    private const int SupportedSchema = 1;
    private const long MaxArtifactSize = 524_288_000;

    public static ManifestCheck Parse(ReadOnlyMemory<byte> bytes)
    {
        JsonDocument document;
        try
        {
            document = JsonDocument.Parse(bytes);
        }
        catch (JsonException)
        {
            return new ManifestCheck.BadManifest("not JSON");
        }

        using (document)
        {
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return new ManifestCheck.BadManifest("not a JSON object");
            }

            if (Integer(root, "schema") != SupportedSchema)
            {
                return new ManifestCheck.BadManifest("unsupported schema");
            }

            var versionText = Text(root, "version");
            var version = versionText is null ? null : SemanticVersion.Parse(versionText);
            if (version is null)
            {
                return new ManifestCheck.BadManifest($"version is not semantic: {versionText}");
            }

            var publishedAt = Text(root, "publishedAt");
            if (publishedAt is null)
            {
                return new ManifestCheck.BadManifest("publishedAt missing");
            }

            if (!root.TryGetProperty("artifacts", out var artifactsJson)
                || artifactsJson.ValueKind != JsonValueKind.Array
                || artifactsJson.GetArrayLength() == 0)
            {
                return new ManifestCheck.BadManifest("no artifacts");
            }

            var artifacts = new List<ReleaseArtifact>();
            foreach (var element in artifactsJson.EnumerateArray())
            {
                var artifact = ParseArtifact(element);
                if (artifact is null)
                {
                    return new ManifestCheck.BadManifest($"invalid artifact {element.GetRawText()}");
                }

                artifacts.Add(artifact);
            }

            var androidVersionCode = Integer(root, "androidVersionCode") is { } code && code >= 1 ? (int?)code : null;
            if (androidVersionCode is null && artifacts.Any(artifact => artifact.Platform == ReleasePlatform.Android))
            {
                return new ManifestCheck.BadManifest("android artifact without androidVersionCode");
            }

            return new ManifestCheck.Valid(
                new ReleaseManifest(version, androidVersionCode, publishedAt, Text(root, "notes"), artifacts));
        }
    }

    private static ReleaseArtifact? ParseArtifact(JsonElement element)
    {
        if (element.ValueKind != JsonValueKind.Object)
        {
            return null;
        }

        ReleasePlatform? platform = Text(element, "platform") switch
        {
            "android" => ReleasePlatform.Android,
            "windows" => ReleasePlatform.Windows,
            _ => null,
        };
        var path = Text(element, "path");
        var sha256 = Text(element, "sha256");
        if (!element.TryGetProperty("size", out var sizeJson)
            || sizeJson.ValueKind != JsonValueKind.Number
            || !sizeJson.TryGetInt64(out var size))
        {
            return null;
        }

        var pathValid = path is not null
            && PathPattern().IsMatch(path)
            && !path.Split('/').Any(segment => segment is ".." or ".");
        if (platform is null || !pathValid || size is < 1 or > MaxArtifactSize
            || sha256 is null || !Sha256Pattern().IsMatch(sha256))
        {
            return null;
        }

        return new ReleaseArtifact(platform.Value, path!, size, sha256);
    }

    private static string? Text(JsonElement element, string name) =>
        element.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String ? value.GetString() : null;

    private static long? Integer(JsonElement element, string name) =>
        element.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.Number && value.TryGetInt64(out var number)
            ? number
            : null;

    // \z, not $: in .NET, $ also matches before a trailing newline.
    [GeneratedRegex(@"^[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*\z", RegexOptions.CultureInvariant)]
    private static partial Regex PathPattern();

    [GeneratedRegex(@"^[0-9a-f]{64}\z", RegexOptions.CultureInvariant)]
    private static partial Regex Sha256Pattern();
}
