using System.Text.RegularExpressions;

namespace GoalMaker.Core.Auth;

/// <summary>
/// The text in the QR code on the sign-in screen (ADR 0020, contracts/vectors/pc-sign-in.json): the
/// prefix and the scan secret, 43 URL-safe base64 characters. It never holds anything that signs in.
/// Beside it the PC shows a two-digit match number, which the phone must pick from three.
/// </summary>
public static partial class PcSignInCode
{
    public const string Prefix = "goalmaker://sign-in/pc/";

    public const int MatchChoices = 3;

    /// <summary>A match number is two digits: 10 to 99.</summary>
    public static bool IsMatchNumber(int value) => value is >= 10 and <= 99;

    /// <summary>The phone's choices are three different match numbers.</summary>
    public static bool IsMatchChoices(IReadOnlyCollection<int> values) =>
        values.Count == MatchChoices && values.All(IsMatchNumber) && values.Distinct().Count() == MatchChoices;

    /// <summary>The text the PC puts in its QR code.</summary>
    public static string QrText(string scanSecret) => Prefix + scanSecret;

    /// <summary>The scan secret in a scanned text, or null when it is not a GoalMaker PC sign-in.</summary>
    public static string? ScanSecretOf(string? text)
    {
        var trimmed = text?.Trim();
        if (trimmed is null || !trimmed.StartsWith(Prefix, StringComparison.Ordinal))
        {
            return null;
        }

        var secret = trimmed[Prefix.Length..];
        return Secret().IsMatch(secret) ? secret : null;
    }

    [GeneratedRegex("^[A-Za-z0-9_-]{43}$")]
    private static partial Regex Secret();
}
