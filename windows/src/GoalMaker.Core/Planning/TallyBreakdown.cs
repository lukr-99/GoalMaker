using System.Text.RegularExpressions;

namespace GoalMaker.Core.Planning;

/// <summary>
/// The Tally page's closer look at this device's own time (docs/tally.md, ADR 0013), pinned by
/// contracts/vectors/tally.json ('window', 'hours', 'apps'), which the Android app runs too: when in the
/// planning day the time went, by hour, and which apps, sites and folders made up each category. It
/// works on the device's raw log and its results never sync.
/// </summary>
public static partial class TallyBreakdown
{
    private static readonly HashSet<string> Browsers = ["chrome.exe", "msedge.exe", "firefox.exe", "brave.exe", "opera.exe", "vivaldi.exe", "arc.exe"];
    private static readonly HashSet<string> Editors = ["code.exe", "studio64.exe", "devenv.exe"];

    /// <summary>
    /// What an app row shows under it: an editor's folder, or the site a browser's tab is on (the part of
    /// the title just before the browser's own name). Null for any other app and for no title.
    /// </summary>
    public static string? WindowLabel(string app, string? title)
    {
        var text = (title ?? string.Empty).Trim();
        if (text.Length == 0)
        {
            return null;
        }

        var name = app.Trim().ToLowerInvariant();
        if (Editors.Contains(name))
        {
            return TallyRules.EditorFolder(app, title);
        }

        if (!Browsers.Contains(name))
        {
            return null;
        }

        var parts = Separator().Split(text);
        if (parts.Length < 2)
        {
            return null;
        }

        var site = Count().Replace(parts[^2].Trim(), string.Empty).Trim();
        return site.Length == 0 ? null : site;
    }

    /// <summary>
    /// What kind of rule a window label makes: an editor's label is a folder, a browser's is words in
    /// its title.
    /// </summary>
    public static string WindowMatch(string app) => Editors.Contains(app.Trim().ToLowerInvariant()) ? TallyRules.Folder : TallyRules.Title;

    /// <summary>
    /// The 24 clock hours of the planning <paramref name="day"/>, from <paramref name="startHour"/> on,
    /// each with the seconds the stretches spent in it by category, most first. Time two stretches share
    /// counts once, for the one that started first, as in <see cref="TallyRules.DayTotals"/>.
    /// </summary>
    public static IReadOnlyList<TallyHour> Hours(IEnumerable<TallyStretch> stretches, DateOnly day, int startHour)
    {
        var from = day.ToDateTime(new TimeOnly(startHour, 0));
        var slots = Enumerable.Range(0, 24).Select(_ => new Dictionary<string, long>(StringComparer.Ordinal)).ToList();
        foreach (var (start, end, stretch) in Once(stretches, from, from.AddDays(1)))
        {
            var at = start;
            while (at < end)
            {
                var index = (int)((at - from).Ticks / TimeSpan.TicksPerHour);
                var next = from.AddHours(index + 1);
                var until = end < next ? end : next;
                slots[index][stretch.Category] = slots[index].GetValueOrDefault(stretch.Category) + (until - at).Ticks;
                at = until;
            }
        }

        return [.. slots.Select((slot, index) => new TallyHour(
            (startHour + index) % 24,
            Seconds(slot.Values.Sum()),
            [.. slot.Select(pair => new TallySeconds(pair.Key, Seconds(pair.Value)))
                .OrderByDescending(part => part.Seconds)
                .ThenBy(part => part.Category, StringComparer.Ordinal)]))];
    }

    /// <summary>
    /// The stretches from the planning day <paramref name="from"/> to <paramref name="to"/>, both
    /// included, by category, then app, then site or folder (<see cref="WindowLabel"/>): each one's time
    /// rounded to the nearest minute, those with none left out, the most first and then by name. Time
    /// two stretches share counts once.
    /// </summary>
    public static IReadOnlyList<TallyCategoryApps> Apps(IEnumerable<TallyStretch> stretches, DateOnly from, DateOnly to, int startHour)
    {
        var categories = new Dictionary<string, long>(StringComparer.Ordinal);
        var apps = new Dictionary<(string Category, string App), long>();
        var windows = new Dictionary<(string Category, string App, string Label), long>();
        var start = from.ToDateTime(new TimeOnly(startHour, 0));
        var end = to.AddDays(1).ToDateTime(new TimeOnly(startHour, 0));
        foreach (var (begin, finish, stretch) in Once(stretches, start, end))
        {
            var ticks = (finish - begin).Ticks;
            var app = stretch.App.Trim().ToLowerInvariant();
            categories[stretch.Category] = categories.GetValueOrDefault(stretch.Category) + ticks;
            apps[(stretch.Category, app)] = apps.GetValueOrDefault((stretch.Category, app)) + ticks;
            if (WindowLabel(stretch.App, stretch.Title) is { } label)
            {
                windows[(stretch.Category, app, label)] = windows.GetValueOrDefault((stretch.Category, app, label)) + ticks;
            }
        }

        return
        [
            .. categories
                .Select(category => new TallyCategoryApps(
                    category.Key,
                    Minutes(category.Value),
                    [
                        .. apps.Where(app => app.Key.Category == category.Key)
                            .Select(app => new TallyAppTime(
                                app.Key.App,
                                Minutes(app.Value),
                                [
                                    .. windows.Where(window => window.Key.Category == category.Key && window.Key.App == app.Key.App)
                                        .Select(window => new TallyWindowTime(window.Key.Label, Minutes(window.Value)))
                                        .Where(window => window.Minutes > 0)
                                        .OrderByDescending(window => window.Minutes)
                                        .ThenBy(window => window.Label, StringComparer.Ordinal),
                                ]))
                            .Where(app => app.Minutes > 0)
                            .OrderByDescending(app => app.Minutes)
                            .ThenBy(app => app.App, StringComparer.Ordinal),
                    ]))
                .Where(category => category.Minutes > 0)
                .OrderByDescending(category => category.Minutes)
                .ThenBy(category => category.Category, StringComparer.Ordinal),
        ];
    }

    // The stretches in start order, cut to the window, each without the time an earlier one already had.
    private static IEnumerable<(DateTime Start, DateTime End, TallyStretch Stretch)> Once(IEnumerable<TallyStretch> stretches, DateTime from, DateTime to)
    {
        var covered = DateTime.MinValue;
        foreach (var stretch in stretches.OrderBy(one => one.Start))
        {
            var start = stretch.Start > covered ? stretch.Start : covered;
            if (stretch.End <= start)
            {
                continue;
            }

            covered = stretch.End;
            var begin = start > from ? start : from;
            var end = stretch.End < to ? stretch.End : to;
            if (end > begin)
            {
                yield return (begin, end, stretch);
            }
        }
    }

    private static int Seconds(long ticks) => (int)(ticks / TimeSpan.TicksPerSecond);

    // Half a minute rounds up, as in TallyRules.DayTotals.
    private static int Minutes(long ticks) => (int)Math.Round((double)ticks / TimeSpan.TicksPerMinute, MidpointRounding.AwayFromZero);

    // Where a browser's title is cut: a hyphen, a bar, a middle dot, an en dash or an em dash between spaces.
    [GeneratedRegex(" (?:-|\\||·|–|—) ")]
    private static partial Regex Separator();

    // A count of new things a site puts first, like "(3) ".
    [GeneratedRegex(@"^\(\d+\+?\)\s*")]
    private static partial Regex Count();
}
