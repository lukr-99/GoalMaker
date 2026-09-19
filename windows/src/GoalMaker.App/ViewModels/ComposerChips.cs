using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;
using System.Windows.Media;
using GoalMaker.App.Localization;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Planning;
using Wpf.Ui.Controls;

namespace GoalMaker.App.ViewModels;

/// <summary>Builds the composer's preview chips (docs/composer.md), in the order the item reads.</summary>
public static partial class ComposerChips
{
    private static readonly DayOfWeek[] MondayFirst =
        [DayOfWeek.Monday, DayOfWeek.Tuesday, DayOfWeek.Wednesday, DayOfWeek.Thursday, DayOfWeek.Friday, DayOfWeek.Saturday, DayOfWeek.Sunday];

    public static IReadOnlyList<ComposerChipViewModel> Build(
        string line,
        ComposerDraft draft,
        DateOnly today,
        IReadOnlyList<AreaItem> areas,
        IReadOnlyList<string> tagNames,
        IStrings strings,
        Func<string, Brush?> areaBrush,
        Action<ComposerChipViewModel> remove)
    {
        var chips = new List<ComposerChipViewModel>();
        IReadOnlyList<ComposerSpan> Of(SpanKind kind) => [.. draft.Spans.Where(span => span.Kind == kind)];
        void Add(SpanKind kind, string label, string? note, SymbolRegular symbol, IReadOnlyList<ComposerSpan> spans, Brush? brush = null, bool muted = false) =>
            chips.Add(new ComposerChipViewModel(
                kind,
                note is null ? label : $"{label} · {note}",
                strings.Get("Composer.Remove", label),
                symbol,
                brush,
                muted,
                spans,
                remove));

        if (draft.Command is { } command)
        {
            var runs = command.Name == PlanRules.Command;
            var note = strings.Get(runs ? "Composer.OpensPlan" : command.Known ? "Composer.Soon" : "Composer.UnknownCommand");
            Add(SpanKind.Command, "/" + command.Name, note, runs ? SymbolRegular.CalendarEdit24 : SymbolRegular.Code24, Of(SpanKind.Command), muted: !runs);
            return chips;
        }

        if (draft.PlannedDate is { } date)
        {
            var label = date == today ? strings.Get("Composer.Today")
                : date == today.AddDays(1) ? strings.Get("Composer.Tomorrow")
                : date.ToString(date.Year == today.Year ? "ddd d MMM" : "ddd d MMM yyyy", CultureInfo.CurrentCulture);
            Add(SpanKind.Date, label, null, SymbolRegular.CalendarLtr24, Of(SpanKind.Date));
        }

        if (draft.PlannedTime is { } time)
        {
            Add(SpanKind.Time, time.ToString("t", CultureInfo.CurrentCulture), null, SymbolRegular.Clock24, Of(SpanKind.Time));
        }

        if (draft.Repeat is { } rule)
        {
            Add(SpanKind.Repeat, DescribeRepeat(rule, strings), null, SymbolRegular.ArrowRepeatAll24, Of(SpanKind.Repeat));
        }

        if (draft.Area is { } areaName)
        {
            var existing = areas.FirstOrDefault(area => Key(area.Name) == Key(areaName));
            Add(SpanKind.Area, existing?.Name ?? areaName, existing is null ? strings.Get("Composer.New") : null, SymbolRegular.Circle24, Of(SpanKind.Area),
                existing is null ? null : areaBrush(existing.ColorId));
        }

        var known = tagNames.Select(Key).ToHashSet(StringComparer.Ordinal);
        foreach (var tag in draft.Tags)
        {
            IReadOnlyList<ComposerSpan> spans = [.. Of(SpanKind.Tag).Where(span => Key(line[(span.Start + 1)..span.End].TrimEnd('.', ',', ';', ':', '!', '?')) == Key(tag))];
            Add(SpanKind.Tag, "#" + tag, known.Contains(Key(tag)) ? null : strings.Get("Composer.New"), SymbolRegular.NumberSymbol24, spans);
        }

        if (draft.TopPriority)
        {
            Add(SpanKind.Priority, strings.Get("Composer.Priority"), null, SymbolRegular.Flag24, Of(SpanKind.Priority));
        }

        if (draft.Project is { } project)
        {
            Add(SpanKind.Project, "+" + project, strings.Get("Composer.Later"), SymbolRegular.Folder24, Of(SpanKind.Project), muted: true);
        }

        if (draft.Idea)
        {
            Add(SpanKind.Idea, strings.Get("Composer.Idea"), strings.Get("Composer.Later"), SymbolRegular.Lightbulb24, Of(SpanKind.Idea), muted: true);
        }

        return chips;
    }

    /// <summary>The line without the given parts, spaces tidied.</summary>
    public static string RemoveParts(string line, IEnumerable<ComposerSpan> spans)
    {
        var text = new StringBuilder(line);
        foreach (var span in spans.OrderByDescending(span => span.Start))
        {
            text.Remove(span.Start, span.End - span.Start);
        }

        return Whitespace().Replace(text.ToString(), " ").Trim();
    }

    private static string Key(string name) => name.Trim().ToLowerInvariant();

    /// <summary>A repeat rule in words, for the composer's chip and the task's detail page.</summary>
    internal static string DescribeRepeat(string rule, IStrings strings)
    {
        var parts = rule.Split(';').Select(part => part.Split('=', 2)).ToDictionary(pair => pair[0], pair => pair.Length > 1 ? pair[1] : string.Empty, StringComparer.Ordinal);
        var interval = parts.TryGetValue("INTERVAL", out var text) && int.TryParse(text, CultureInfo.InvariantCulture, out var n) ? n : 1;
        var format = CultureInfo.CurrentCulture.DateTimeFormat;
        switch (parts.GetValueOrDefault("FREQ"))
        {
            case "DAILY":
                return interval > 1 ? strings.Get("Repeat.EveryNDays", interval) : strings.Get("Repeat.Daily");
            case "WEEKLY":
                var codes = parts.GetValueOrDefault("BYDAY", string.Empty).Split(',');
                var days = MondayFirst.Where(day => codes.Contains(day.ToString()[..2].ToUpperInvariant())).ToList();
                var names = string.Join(", ", days.Select(format.GetAbbreviatedDayName));
                if (interval > 1)
                {
                    return strings.Get("Repeat.EveryNWeeksOn", interval, names);
                }

                if (days.Count == 5 && days.All(day => day is not (DayOfWeek.Saturday or DayOfWeek.Sunday)))
                {
                    return strings.Get("Repeat.Weekdays");
                }

                if (days.Count == 2 && days.All(day => day is DayOfWeek.Saturday or DayOfWeek.Sunday))
                {
                    return strings.Get("Repeat.Weekend");
                }

                return strings.Get("Repeat.EveryDayName", days.Count == 1 ? format.GetDayName(days[0]) : names);
            case "MONTHLY":
                var monthDay = parts.TryGetValue("BYMONTHDAY", out var dayText) && int.TryParse(dayText, CultureInfo.InvariantCulture, out var d) ? d : 1;
                return interval > 1 ? strings.Get("Repeat.EveryNMonthsOn", interval, monthDay) : strings.Get("Repeat.MonthlyOn", monthDay);
            default:
                return rule;
        }
    }

    [GeneratedRegex(@"\s+", RegexOptions.CultureInvariant)]
    private static partial Regex Whitespace();
}
