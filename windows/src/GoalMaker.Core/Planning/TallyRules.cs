using System.Globalization;
using System.Text.RegularExpressions;

namespace GoalMaker.Core.Planning;

/// <summary>
/// Tally's rules (docs/tally.md, ADR 0013), pinned by contracts/vectors/tally.json, which the Android
/// app and the connector run too: which category and project a moment of foreground time belongs to,
/// when the clock stops for idle, and how a device's intervals become the daily totals that sync.
/// </summary>
public static partial class TallyRules
{
    public const string App = "app";
    public const string Title = "title";
    public const string Folder = "folder";

    public const string Android = "android";
    public const string Windows = "windows";
    public const string Any = "any";

    public const string Phone = "phone";
    public const string Pc = "pc";

    /// <summary>Where time goes that no rule claims.</summary>
    public const string Other = "other";

    /// <summary>The clock stops after this long without input, unless the window is in the Video category.</summary>
    public const int IdleSeconds = 300;

    public const string Video = "video";

    /// <summary>A day holds at most this many minutes, per device and category.</summary>
    public const int MaxMinutes = 1440;

    private const string Namespace = "b8c61b22-5e0c-4f0a-9d1e-6f4a2c7e3b91";
    private const string VsCode = " - Visual Studio Code";
    private const string VisualStudio = " - Microsoft Visual Studio";

    /// <summary>
    /// The folder an editor's window title names: Visual Studio Code's workspace (the part before
    /// " - Visual Studio Code"), Android Studio's project (the part before the first " – "), and Visual
    /// Studio's solution (the part before " - Microsoft Visual Studio", without "(Running)" and the like).
    /// Null for anything that isn't one of these editors.
    /// </summary>
    public static string? EditorFolder(string app, string? title)
    {
        var text = (title ?? string.Empty).Trim();
        if (text.Length == 0)
        {
            return null;
        }

        string? folder = null;
        switch (app.Trim().ToLowerInvariant())
        {
            case "code.exe" when text.EndsWith(VsCode, StringComparison.Ordinal):
                folder = text[..^VsCode.Length].Split(" - ")[^1];
                break;
            case "studio64.exe":
                folder = text.Split(" – ")[0];
                break;
            case "devenv.exe":
                var end = text.IndexOf(VisualStudio, StringComparison.Ordinal);
                if (end > 0)
                {
                    folder = Running().Replace(text[..end], string.Empty);
                }

                break;
        }

        var cleaned = folder is null ? string.Empty : Unsaved().Replace(folder, string.Empty).Trim();
        return cleaned.Length == 0 ? null : cleaned;
    }

    /// <summary>
    /// The project an editor's folder belongs to: the one project whose local folder has that name. None
    /// when no project, or more than one, has it.
    /// </summary>
    public static string? ProjectFor(string? folder, IEnumerable<ProjectItem> projects)
    {
        if (FolderName(folder) is not { } name)
        {
            return null;
        }

        var found = projects.Where(project => FolderName(project.LocalFolder) == name).Take(2).ToList();
        return found.Count == 1 ? found[0].Id : null;
    }

    /// <summary>
    /// Where a sample goes: the first of the owner's rules that matches, then the first default, then
    /// Other. A rule's own project wins; otherwise, on Windows, the editor's folder names the project.
    /// </summary>
    public static TallySort SortSample(
        TallySample sample, IEnumerable<TallyRule> own, IEnumerable<TallyRule> defaults, IEnumerable<ProjectItem>? projects = null)
    {
        var windows = sample.Platform == Windows;
        var folder = windows ? EditorFolder(sample.App, sample.Title) : null;
        var rule = own.Concat(defaults).FirstOrDefault(one => Matches(one, sample, folder));
        // The phone never links time to a project, not even through a rule (docs/tally.md).
        var project = windows ? rule?.Project ?? ProjectFor(folder, projects ?? []) : null;
        return new TallySort(rule?.Category ?? Other, project);
    }

    /// <summary>Whether the clock runs: not while locked or asleep, and not after five idle minutes unless it is video.</summary>
    public static bool Counts(int secondsSinceInput, string category, bool locked, bool asleep) =>
        !locked && !asleep && (category == Video || secondsSinceInput < IdleSeconds);

    /// <summary>
    /// A device's intervals as planning-day totals: each interval is cut at the hour the day starts,
    /// time two intervals share is counted once (for the one that started first), and each day, category
    /// and project gets its seconds rounded to the nearest minute (half a minute up), at most a whole day.
    /// Rows with no minutes are left out; the rest are ordered by day, category and project (none first).
    /// </summary>
    public static IReadOnlyList<TallyTotal> DayTotals(IEnumerable<TallyInterval> intervals, int startHour)
    {
        var ticks = new Dictionary<(DateOnly Day, string Category, string? Project), long>();
        var covered = DateTime.MinValue;
        foreach (var interval in intervals.OrderBy(one => one.Start))
        {
            var from = interval.Start > covered ? interval.Start : covered;
            var to = interval.End;
            if (to <= from)
            {
                continue;
            }

            covered = to;
            while (from < to)
            {
                var day = PlanningDay.Of(from, startHour);
                var nextStart = day.AddDays(1).ToDateTime(new TimeOnly(startHour, 0));
                var until = to < nextStart ? to : nextStart;
                var key = (day, interval.Category, interval.Project);
                ticks[key] = ticks.GetValueOrDefault(key) + (until - from).Ticks;
                from = until;
            }
        }

        return
        [
            .. ticks
                .Select(pair => new TallyTotal(
                    pair.Key.Day,
                    pair.Key.Category,
                    pair.Key.Project,
                    (int)Math.Min(MaxMinutes, Math.Round((double)pair.Value / TimeSpan.TicksPerMinute, MidpointRounding.AwayFromZero))))
                .Where(total => total.Minutes > 0)
                .OrderBy(total => total.Day)
                .ThenBy(total => total.Category, StringComparer.Ordinal)
                .ThenBy(total => total.Project ?? string.Empty, StringComparer.Ordinal),
        ];
    }

    /// <summary>The id every device gives its row for a day, category and project, so rewriting a day replaces it.</summary>
    public static string DayId(string owner, DateOnly day, string device, string category, string? project) =>
        NameBasedUuid.Of(
            Namespace,
            $"tally/{owner.ToLowerInvariant()}/{day.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture)}/{device.ToLowerInvariant()}/{category}/{project ?? "-"}");

    // The last part of a path, or the whole of a bare name, ignoring case.
    private static string? FolderName(string? path)
    {
        var name = Separator().Split(TrailingSeparators().Replace((path ?? string.Empty).Trim(), string.Empty))[^1].Trim().ToLowerInvariant();
        return name.Length == 0 ? null : name;
    }

    private static bool Matches(TallyRule rule, TallySample sample, string? folder)
    {
        if (rule.Platform != Any && rule.Platform != sample.Platform)
        {
            return false;
        }

        var pattern = rule.Pattern.Trim().ToLowerInvariant();
        if (pattern.Length == 0)
        {
            return false;
        }

        return rule.Match switch
        {
            App => sample.App.Trim().ToLowerInvariant() == pattern,
            Title => sample.Platform == Windows && (sample.Title ?? string.Empty).ToLowerInvariant().Contains(pattern, StringComparison.Ordinal),
            Folder => FolderName(folder) is { } name && name == FolderName(pattern),
            _ => false,
        };
    }

    [GeneratedRegex(@"\s*\([^)]*\)\s*$")]
    private static partial Regex Running();

    [GeneratedRegex(@"^[●\s]+")]
    private static partial Regex Unsaved();

    [GeneratedRegex(@"[\\/]+$")]
    private static partial Regex TrailingSeparators();

    [GeneratedRegex(@"[\\/]")]
    private static partial Regex Separator();
}
