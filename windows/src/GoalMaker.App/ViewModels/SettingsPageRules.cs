using DotNetLib.Tray;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The Settings page's jump navigation and hints as contracts/vectors/settings.json states them,
/// shared with the phone's SettingsPageRules. The settings kit's page runs its own copy of these
/// rules (<see cref="SettingsSectionTracker"/>, <see cref="SettingsLayoutRules"/>,
/// <see cref="SettingsMotion"/>); this thin layer asks the kit and fills in where the contract says
/// more than the kit does: the first visible section before any is measured, a jump target that is
/// visible but not measured yet, whole milliseconds, and the scroll hint's rule as a plain function.
/// SettingsPageRulesContractTests holds both to the vector.
/// </summary>
public static class SettingsPageRules
{
    // The kit reads the bottom from the viewport and the content's height; the contract gives how far
    // the page can scroll. Any viewport works, as only the difference counts.
    private const double Viewport = 1000;

    /// <summary>Whether the section list (or the chips on a narrow window) shows: 4 visible sections or more.</summary>
    public static bool ShowsNavigation(int visibleSections) => SettingsLayoutRules.ShowsNavigation(visibleSections);

    /// <summary>
    /// The section being read: the <paramref name="pinned"/> jump target while it is visible; otherwise
    /// the last section whose top is at or above the line 80 below the top of the scroll area, or the
    /// last one at the bottom of a page that scrolls. Sections without a top are skipped; before any is
    /// measured the first visible section is current, and with none there is none.
    /// </summary>
    public static string? CurrentAt(
        IReadOnlyList<string> sections,
        IReadOnlyDictionary<string, double> tops,
        double scroll,
        double maxScroll,
        string? pinned = null)
    {
        ArgumentNullException.ThrowIfNull(sections);
        ArgumentNullException.ThrowIfNull(tops);

        if (pinned is not null && sections.Contains(pinned))
        {
            return pinned;
        }

        var measured = sections.Where(tops.ContainsKey).Select(id => new SectionPosition(id, tops[id])).ToList();
        if (measured.Count == 0)
        {
            return sections.FirstOrDefault();
        }

        return SettingsSectionTracker.CurrentAt(measured, scroll, Viewport, Viewport + Math.Max(0, maxScroll));
    }

    /// <summary>How long a jump scrolls over <paramref name="distance"/>, in whole milliseconds: 250 to 450, instant when tiny or with reduce motion.</summary>
    public static int JumpScrollMilliseconds(double distance, bool reduceMotion) =>
        (int)Math.Round(SettingsMotion.JumpScrollDuration(distance, reduceMotion).TotalMilliseconds, MidpointRounding.AwayFromZero);

    /// <summary>What a card plays after a jump or a scroll, or null when nothing plays.</summary>
    public static HighlightTimeline? Hint(SettingsHintKind kind, bool reduceMotion) => SettingsMotion.Hint(kind, reduceMotion);

    /// <summary>
    /// Whether the scroll hint plays for <paramref name="current"/> once the page has been still for
    /// <see cref="SettingsMotion.ScrollIdleDelay"/>: only for a section other than the one hinted last,
    /// never during a jump, never with reduce motion.
    /// </summary>
    public static bool PlaysScrollHint(string? current, string? lastHinted, bool jumping, bool reduceMotion) =>
        current is not null && current != lastHinted && !jumping && SettingsMotion.ScrollHint(reduceMotion) is not null;
}
