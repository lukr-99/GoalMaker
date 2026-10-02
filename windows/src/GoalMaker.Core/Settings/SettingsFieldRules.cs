using System.Globalization;
using System.Text.RegularExpressions;

namespace GoalMaker.Core.Settings;

/// <summary>
/// What the Settings text fields accept, the same as the phone's. A field checks its text when the
/// owner presses Enter or leaves it, never on every key, and a value these refuse is never saved.
/// </summary>
public static partial class SettingsFieldRules
{
    /// <summary>A time of day written as 22:00 or 7:30, or null when it is not one.</summary>
    public static TimeOnly? Time(string? text)
    {
        var match = Clock().Match(text?.Trim() ?? string.Empty);
        return match.Success
            ? new TimeOnly(int.Parse(match.Groups[1].Value, CultureInfo.InvariantCulture), int.Parse(match.Groups[2].Value, CultureInfo.InvariantCulture))
            : null;
    }

    /// <summary>A time as the fields show it: 22:00, 07:30.</summary>
    public static string Format(TimeOnly time) => time.ToString("HH:mm", CultureInfo.InvariantCulture);

    /// <summary>Whether <paramref name="text"/> is an http or https address with a host, as a backend URL has to be.</summary>
    public static bool BackendUrl(string? text) => Address().IsMatch(text?.Trim() ?? string.Empty);

    [GeneratedRegex(@"^([01]?\d|2[0-3]):([0-5]\d)$", RegexOptions.CultureInvariant)]
    private static partial Regex Clock();

    [GeneratedRegex(@"^https?://[^\s/:?#]+(:\d{1,5})?(/\S*)?$", RegexOptions.IgnoreCase | RegexOptions.CultureInvariant)]
    private static partial Regex Address();
}
