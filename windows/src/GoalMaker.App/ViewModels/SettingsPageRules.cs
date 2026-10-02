using DotNetLib.Tray;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The Settings page's jump navigation and hints as contracts/vectors/settings.json states them,
/// shared with the phone's SettingsPageRules. The settings kit's page runs these rules itself
/// (<see cref="SettingsLayoutRules"/>, <see cref="SettingsSectionTracker"/>, <see cref="SettingsMotion"/>);
/// this names them as the phone does, so SettingsPageRulesContractTests holds the kit to the vector.
/// </summary>
public static class SettingsPageRules
{
    /// <summary>Whether the section list (or the chips on a narrow window) shows: 4 visible sections or more.</summary>
    public static bool ShowsNavigation(int visibleSections) => SettingsLayoutRules.ShowsNavigation(visibleSections);

    /// <summary>
    /// The section being read: the <paramref name="pinned"/> jump target while it is visible; otherwise
    /// the last section whose top passed the line 80 below the top of the scroll area, or the last one
    /// at the bottom of a page that scrolls. Before any is measured, the first visible section.
    /// </summary>
    public static string? CurrentAt(
        IReadOnlyList<string> sections,
        IReadOnlyDictionary<string, double> tops,
        double scroll,
        double maxScroll,
        string? pinned = null) =>
        SettingsSectionTracker.CurrentAt(sections, tops, scroll, maxScroll, pinned);

    /// <summary>How long a jump scrolls over <paramref name="distance"/>, in whole milliseconds: 250 to 450, instant when tiny or with reduce motion.</summary>
    public static int JumpScrollMilliseconds(double distance, bool reduceMotion) =>
        (int)SettingsMotion.JumpScrollDuration(distance, reduceMotion).TotalMilliseconds;

    /// <summary>What a card plays after a jump or a scroll, or null when nothing plays.</summary>
    public static HighlightTimeline? Hint(SettingsHintKind kind, bool reduceMotion) => SettingsMotion.Hint(kind, reduceMotion);

    /// <summary>
    /// Whether the scroll hint plays for <paramref name="current"/> once the page has been still for
    /// <see cref="SettingsMotion.ScrollIdleDelay"/>: only for a section other than the one hinted last,
    /// never during a jump, never with reduce motion.
    /// </summary>
    public static bool PlaysScrollHint(string? current, string? lastHinted, bool jumping, bool reduceMotion) =>
        SettingsMotion.PlaysScrollHint(current, lastHinted, jumping, reduceMotion);
}
