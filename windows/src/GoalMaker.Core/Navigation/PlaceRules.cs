namespace GoalMaker.Core.Navigation;

/// <summary>
/// Pinned places and the Places hub (ADR 0014, docs/design/spec.md, Navigation), pinned by
/// contracts/vectors/navigation.json, which the Android app runs too. A place is a top-level page
/// named by its id; Settings is not one.
/// </summary>
public static class PlaceRules
{
    public const string Today = "today";
    public const string Tomorrow = "tomorrow";
    public const string Inbox = "inbox";
    public const string Calendar = "calendar";
    public const string Habits = "habits";
    public const string Goals = "goals";
    public const string Projects = "projects";
    public const string Wants = "wants";
    public const string Reviews = "reviews";
    public const string Stats = "stats";
    public const string Archive = "archive";

    private const int PhoneLimit = 4;

    /// <summary>Every place, in the order the Places hub and All places list them.</summary>
    public static readonly IReadOnlyList<string> Places =
        [Today, Tomorrow, Inbox, Calendar, Habits, Goals, Projects, Wants, Reviews, Stats, Archive];

    /// <summary>How many pins a device holds, or null for no limit.</summary>
    public static int? Limit(DeviceKind device) => device == DeviceKind.Phone ? PhoneLimit : null;

    public static IReadOnlyList<string> Defaults(DeviceKind device) => [Today, Tomorrow, Inbox, Projects];

    /// <summary>Adds <paramref name="place"/> at the end, unless it is unknown, already pinned or one pin too many.</summary>
    public static PinResult Pin(IReadOnlyList<string> pins, string place, DeviceKind device)
    {
        var full = Limit(device) is { } limit && pins.Count >= limit;
        if (!Places.Contains(place) || pins.Contains(place) || full)
        {
            return new PinResult(pins, Refused: true);
        }

        return new PinResult([.. pins, place], Refused: false);
    }

    /// <summary>Removes <paramref name="place"/>, unless it is not pinned or is the last pin.</summary>
    public static PinResult Unpin(IReadOnlyList<string> pins, string place)
    {
        if (!pins.Contains(place) || pins.Count <= 1)
        {
            return new PinResult(pins, Refused: true);
        }

        return new PinResult([.. pins.Where(pin => pin != place)], Refused: false);
    }

    /// <summary>The pins read back from settings: known places once each, within the limit, else the defaults.</summary>
    public static IReadOnlyList<string> Stored(IReadOnlyList<string>? stored, DeviceKind device)
    {
        var known = (stored ?? []).Where(Places.Contains).Distinct().ToList();
        var kept = Limit(device) is { } limit ? known.Take(limit).ToList() : known;
        return kept.Count == 0 ? Defaults(device) : kept;
    }

    /// <summary>The number on the Places tab: what waits in the places that are not pinned.</summary>
    public static int Count(IReadOnlyList<string> pins, IReadOnlyDictionary<string, int> waiting) =>
        waiting.Where(entry => !pins.Contains(entry.Key)).Sum(entry => entry.Value);
}
