using System.Text.RegularExpressions;

namespace GoalMaker.Core.Updates;

/// <summary>
/// Where the update channel's files live on GitHub Releases (ADR 0010), given the repository's web
/// address. Every rule is covered by contracts/vectors/release-channel.json.
/// </summary>
public sealed partial class ReleaseChannelAddress
{
    private readonly string root;

    /// <param name="updateUrl">The repository page, for example https://github.com/lukr-99/GoalMaker. A trailing slash is ignored.</param>
    public ReleaseChannelAddress(string updateUrl)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(updateUrl);
        root = updateUrl.Trim().TrimEnd('/');
    }

    /// <summary>The latest release's manifest.</summary>
    public string Manifest => root + "/releases/latest/download/manifest.json";

    /// <summary>The detached signature over the latest release's manifest.</summary>
    public string Signature => root + "/releases/latest/download/manifest.sig";

    /// <summary>The latest release's page, where the owner can download a release by hand.</summary>
    public string ReleasesPage => root + "/releases/latest";

    /// <summary>
    /// The address of a manifest artifact path "X.Y.Z/file": the asset on the release tagged vX.Y.Z.
    /// Null for any other path, which must never be downloaded.
    /// </summary>
    public string? Artifact(string path)
    {
        if (ArtifactPathPattern().Match(path) is not { Success: true } match)
        {
            return null;
        }

        var file = match.Groups["file"].Value;
        return file is "." or ".."
            ? null
            : $"{root}/releases/download/v{match.Groups["version"].Value}/{file}";
    }

    // A plain version without leading zeros or a pre-release, then one file name. \z, not $: in .NET,
    // $ also matches before a trailing newline.
    [GeneratedRegex(
        @"^(?<version>(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*))/(?<file>[A-Za-z0-9._-]+)\z",
        RegexOptions.CultureInvariant)]
    private static partial Regex ArtifactPathPattern();
}
