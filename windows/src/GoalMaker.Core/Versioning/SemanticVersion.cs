using System.Globalization;
using System.Text.RegularExpressions;

namespace GoalMaker.Core.Versioning;

/// <summary>
/// A Semantic Versioning 2.0.0 version. Build metadata is accepted and ignored for precedence.
/// Behavior is fixed by contracts/vectors/semantic-version.json, shared with the Android app.
/// </summary>
public sealed partial class SemanticVersion : IComparable<SemanticVersion>, IEquatable<SemanticVersion>
{
    public const string DevelopmentIdentifier = "dev";

    private SemanticVersion(long major, long minor, long patch, IReadOnlyList<string> prerelease)
    {
        Major = major;
        Minor = minor;
        Patch = patch;
        Prerelease = prerelease;
    }

    public long Major { get; }

    public long Minor { get; }

    public long Patch { get; }

    public IReadOnlyList<string> Prerelease { get; }

    public bool IsPrerelease => Prerelease.Count > 0;

    /// <summary>True for local development builds, which carry the <c>dev</c> pre-release identifier.</summary>
    public bool IsDevelopmentBuild => IsPrerelease && Prerelease[0] == DevelopmentIdentifier;

    /// <summary>Parses strictly (no leading v, no spaces); returns null when the text isn't valid.</summary>
    public static SemanticVersion? Parse(string text)
    {
        var match = Pattern().Match(text);
        if (!match.Success
            || !long.TryParse(match.Groups[1].Value, NumberStyles.None, CultureInfo.InvariantCulture, out var major)
            || !long.TryParse(match.Groups[2].Value, NumberStyles.None, CultureInfo.InvariantCulture, out var minor)
            || !long.TryParse(match.Groups[3].Value, NumberStyles.None, CultureInfo.InvariantCulture, out var patch))
        {
            return null;
        }

        var prerelease = match.Groups[4].Success ? match.Groups[4].Value.Split('.') : [];
        return new SemanticVersion(major, minor, patch, prerelease);
    }

    public int CompareTo(SemanticVersion? other)
    {
        if (other is null)
        {
            return 1;
        }

        var result = Major.CompareTo(other.Major);
        if (result == 0)
        {
            result = Minor.CompareTo(other.Minor);
        }

        if (result == 0)
        {
            result = Patch.CompareTo(other.Patch);
        }

        return result != 0 ? result : ComparePrerelease(Prerelease, other.Prerelease);
    }

    public bool Equals(SemanticVersion? other) => other is not null && CompareTo(other) == 0;

    public override bool Equals(object? obj) => Equals(obj as SemanticVersion);

    public override int GetHashCode() => ToString().GetHashCode(StringComparison.Ordinal);

    public override string ToString() =>
        $"{Major}.{Minor}.{Patch}" + (IsPrerelease ? "-" + string.Join('.', Prerelease) : string.Empty);

    public static bool operator >(SemanticVersion left, SemanticVersion right) => left.CompareTo(right) > 0;

    public static bool operator <(SemanticVersion left, SemanticVersion right) => left.CompareTo(right) < 0;

    public static bool operator >=(SemanticVersion left, SemanticVersion right) => left.CompareTo(right) >= 0;

    public static bool operator <=(SemanticVersion left, SemanticVersion right) => left.CompareTo(right) <= 0;

    private static int ComparePrerelease(IReadOnlyList<string> left, IReadOnlyList<string> right)
    {
        if (left.Count == 0 || right.Count == 0)
        {
            return right.Count.CompareTo(left.Count) switch
            {
                0 => 0,
                > 0 => 1,
                _ => -1,
            };
        }

        for (var index = 0; index < Math.Min(left.Count, right.Count); index++)
        {
            var result = CompareIdentifiers(left[index], right[index]);
            if (result != 0)
            {
                return result;
            }
        }

        return left.Count.CompareTo(right.Count);
    }

    private static int CompareIdentifiers(string left, string right)
    {
        var leftIsNumber = left.All(char.IsAsciiDigit);
        var rightIsNumber = right.All(char.IsAsciiDigit);
        return (leftIsNumber, rightIsNumber) switch
        {
            (true, true) => long.Parse(left, CultureInfo.InvariantCulture)
                .CompareTo(long.Parse(right, CultureInfo.InvariantCulture)),
            (true, false) => -1,
            (false, true) => 1,
            _ => string.CompareOrdinal(left, right),
        };
    }

    // ASCII [0-9], not \d (which matches any Unicode digit in .NET), and \z, not $ (which also
    // matches before a trailing newline). Both cases are in the contract vectors.
    [GeneratedRegex(
        @"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)"
        + @"(?:-((?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)(?:\.(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*))*))?"
        + @"(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?\z",
        RegexOptions.CultureInvariant)]
    private static partial Regex Pattern();
}
