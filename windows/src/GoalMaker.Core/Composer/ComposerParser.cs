using System.Globalization;
using System.Text.RegularExpressions;

namespace GoalMaker.Core.Composer;

/// <summary>
/// Turns one composer line into a draft (docs/composer.md), pinned by
/// contracts/vectors/composer.json. Pure: the caller passes the local time and the day rollover hour.
/// </summary>
public static partial class ComposerParser
{
    public static readonly IReadOnlySet<string> KnownCommands = new HashSet<string>(StringComparer.Ordinal) { "plan", "review", "habit", "goal" };

    private const string Trailing = ".,;:!?";
    private const string TrailingButDot = ",;:!?";

    private static readonly Dictionary<string, DayOfWeek> Weekdays = new(StringComparer.Ordinal)
    {
        ["monday"] = DayOfWeek.Monday, ["mon"] = DayOfWeek.Monday,
        ["tuesday"] = DayOfWeek.Tuesday, ["tue"] = DayOfWeek.Tuesday, ["tues"] = DayOfWeek.Tuesday,
        ["wednesday"] = DayOfWeek.Wednesday, ["wed"] = DayOfWeek.Wednesday,
        ["thursday"] = DayOfWeek.Thursday, ["thu"] = DayOfWeek.Thursday, ["thur"] = DayOfWeek.Thursday, ["thurs"] = DayOfWeek.Thursday,
        ["friday"] = DayOfWeek.Friday, ["fri"] = DayOfWeek.Friday,
        ["saturday"] = DayOfWeek.Saturday, ["sat"] = DayOfWeek.Saturday,
        ["sunday"] = DayOfWeek.Sunday, ["sun"] = DayOfWeek.Sunday,
    };

    private static readonly Dictionary<string, int> Months = BuildMonths();

    private static readonly HashSet<DayOfWeek> WorkWeek =
        [DayOfWeek.Monday, DayOfWeek.Tuesday, DayOfWeek.Wednesday, DayOfWeek.Thursday, DayOfWeek.Friday];

    private static readonly HashSet<DayOfWeek> Weekend = [DayOfWeek.Saturday, DayOfWeek.Sunday];

    private static readonly HashSet<string> RelativeDayWords = new(StringComparer.Ordinal) { "today", "tomorrow", "tmrw", "tmr" };

    public static ComposerDraft Parse(string line, DateTime now, int rolloverHour = 4)
    {
        if (Command(line) is { } command)
        {
            return command;
        }

        var tokens = Tokenize(line);
        var today = DateOnly.FromDateTime(now.AddHours(-rolloverHour));
        var markers = tokens.Select(MarkerOf).ToList();
        var candidates = Enumerable.Range(0, tokens.Count).SelectMany(start => PhrasesAt(tokens, start, today)).ToList();

        // Dates, times and repeats count in the line's tail, then at its start (docs/composer.md).
        var accepted = new List<Phrase>();
        var index = tokens.Count - 1;
        while (index >= 0)
        {
            if (markers[index] is not null)
            {
                index--;
                continue;
            }

            var end = index + 1;
            var phrase = candidates.Where(p => p.End == end).OrderBy(p => p.Start).FirstOrDefault();
            if (phrase is null)
            {
                break;
            }

            accepted.Add(phrase);
            index = phrase.Start - 1;
        }

        var headLimit = index;
        var position = 0;
        while (position <= headLimit)
        {
            if (markers[position] is not null)
            {
                position++;
                continue;
            }

            var start = position;
            var phrase = candidates.Where(p => p.Start == start && p.End - 1 <= headLimit).OrderByDescending(p => p.End).FirstOrDefault();
            if (phrase is null)
            {
                break;
            }

            accepted.Add(phrase);
            position = phrase.End;
        }

        var consumed = new bool[tokens.Count];
        for (var i = 0; i < tokens.Count; i++)
        {
            consumed[i] = markers[i] is not null;
        }

        foreach (var phrase in accepted)
        {
            for (var i = phrase.Start; i < phrase.End; i++)
            {
                consumed[i] = true;
            }
        }

        // The last one wins (docs/composer.md): the phrase that starts furthest right.
        Phrase? Last(SpanKind kind) => accepted.Where(p => p.Kind == kind).OrderByDescending(p => p.Start).FirstOrDefault();
        var date = Last(SpanKind.Date)?.Date;
        var time = Last(SpanKind.Time)?.Time;
        var repeat = Last(SpanKind.Repeat)?.Repeat;
        var earliest = time is { } t && date is null && today.ToDateTime(t) < now ? today.AddDays(1) : today;
        var planned = date ?? repeat?.FirstOccurrence(earliest) ?? (time is not null ? earliest : null);

        var tags = new List<string>();
        var seen = new HashSet<string>(StringComparer.Ordinal);
        foreach (var marker in markers.OfType<Marker>().Where(m => m.Kind == SpanKind.Tag))
        {
            if (seen.Add(marker.Name.ToLowerInvariant()))
            {
                tags.Add(marker.Name);
            }
        }

        var spans = tokens.Select((token, i) => markers[i] is { } marker ? new ComposerSpan(marker.Kind, token.Start, token.End) : null)
            .OfType<ComposerSpan>()
            .Concat(accepted.Select(p => new ComposerSpan(p.Kind, tokens[p.Start].Start, tokens[p.End - 1].End)))
            .OrderBy(span => span.Start)
            .ToList();

        return new ComposerDraft(
            string.Join(' ', tokens.Where((_, i) => !consumed[i]).Select(token => token.Text)),
            planned,
            time,
            tags,
            markers.LastOrDefault(m => m?.Kind == SpanKind.Area)?.Name.Replace('_', ' '),
            markers.LastOrDefault(m => m?.Kind == SpanKind.Project)?.Name.Replace('_', ' '),
            markers.Any(m => m?.Kind == SpanKind.Priority),
            markers.Any(m => m?.Kind == SpanKind.Idea),
            repeat?.Rule(planned ?? today),
            null,
            spans);
    }

    private static ComposerDraft? Command(string line)
    {
        var trimmed = line.TrimStart();
        if (trimmed.Length < 2 || trimmed[0] != '/' || !char.IsLetter(trimmed[1]))
        {
            return null;
        }

        var offset = line.Length - trimmed.Length;
        var nameEnd = trimmed.IndexOfAny([' ', '\t', '\n', '\r']);
        if (nameEnd < 0)
        {
            nameEnd = trimmed.Length;
        }

        var name = trimmed[1..nameEnd].ToLowerInvariant();
        return new ComposerDraft(
            string.Empty,
            null,
            null,
            [],
            null,
            null,
            false,
            false,
            null,
            new ComposerCommand(name, trimmed[nameEnd..].Trim(), KnownCommands.Contains(name)),
            [new ComposerSpan(SpanKind.Command, offset, offset + nameEnd)]);
    }

    private static List<Token> Tokenize(string line)
    {
        var tokens = new List<Token>();
        var index = 0;
        while (index < line.Length)
        {
            if (char.IsWhiteSpace(line[index]))
            {
                index++;
                continue;
            }

            var start = index;
            while (index < line.Length && !char.IsWhiteSpace(line[index]))
            {
                index++;
            }

            tokens.Add(new Token(line[start..index], start, index));
        }

        return tokens;
    }

    private static Marker? MarkerOf(Token token)
    {
        if (token.Escaped)
        {
            return null;
        }

        switch (token.Raw)
        {
            case "!":
                return new Marker(SpanKind.Priority, string.Empty);
            case "?":
                return new Marker(SpanKind.Idea, string.Empty);
        }

        var core = token.Raw.TrimEnd(Trailing.ToCharArray());
        if (core.Length < 2 || !MarkerName().IsMatch(core[1..]))
        {
            return null;
        }

        SpanKind? kind = core[0] switch
        {
            '#' => SpanKind.Tag,
            '@' => SpanKind.Area,
            '+' => SpanKind.Project,
            _ => null,
        };
        return kind is { } found ? new Marker(found, core[1..]) : null;
    }

    private static IEnumerable<Phrase> PhrasesAt(List<Token> tokens, int start, DateOnly today)
    {
        if (TimeAt(tokens, start) is var (timeEnd, time))
        {
            yield return new Phrase(SpanKind.Time, start, timeEnd, Time: time);
        }

        if (DateAt(tokens, start, today, allowOn: true) is var (dateEnd, date))
        {
            yield return new Phrase(SpanKind.Date, start, dateEnd, Date: date);
        }

        if (RepeatAt(tokens, start) is var (repeatEnd, repeat))
        {
            yield return new Phrase(SpanKind.Repeat, start, repeatEnd, Repeat: repeat);
        }
    }

    private static string? Word(List<Token> tokens, int index) => index >= 0 && index < tokens.Count ? tokens[index].Word : null;

    private static (int End, TimeOnly Time)? TimeAt(List<Token> tokens, int start)
    {
        var at = Word(tokens, start) == "at" ? start + 1 : start;
        if (Word(tokens, at) is not { } first)
        {
            return null;
        }

        var suffix = Word(tokens, at + 1) is "am" or "pm" ? Word(tokens, at + 1) : null;
        switch (first)
        {
            case "noon":
                return (at + 1, new TimeOnly(12, 0));
            case "midnight":
                return (at + 1, new TimeOnly(0, 0));
        }

        if (Clock12().Match(first) is { Success: true } twelve)
        {
            var minute = twelve.Groups[2].Success ? int.Parse(twelve.Groups[2].Value, CultureInfo.InvariantCulture) : 0;
            return TwelveHour(int.Parse(twelve.Groups[1].Value, CultureInfo.InvariantCulture), minute, twelve.Groups[3].Value) is { } value
                ? (at + 1, value)
                : null;
        }

        if (Clock24().Match(first) is { Success: true } clock)
        {
            var hour = int.Parse(clock.Groups[1].Value, CultureInfo.InvariantCulture);
            var minute = int.Parse(clock.Groups[2].Value, CultureInfo.InvariantCulture);
            if (suffix is not null && TwelveHour(hour, minute, suffix) is { } withSuffix)
            {
                return (at + 2, withSuffix);
            }

            return hour <= 23 && minute <= 59 ? (at + 1, new TimeOnly(hour, minute)) : null;
        }

        if (suffix is not null && HourOnly().IsMatch(first))
        {
            return TwelveHour(int.Parse(first, CultureInfo.InvariantCulture), 0, suffix) is { } value ? (at + 2, value) : null;
        }

        return null;
    }

    private static TimeOnly? TwelveHour(int hour, int minute, string suffix)
    {
        if (hour is < 1 or > 12 || minute > 59)
        {
            return null;
        }

        var converted = (suffix, hour) switch
        {
            ("am", 12) => 0,
            ("am", _) => hour,
            (_, 12) => 12,
            _ => hour + 12,
        };
        return new TimeOnly(converted, minute);
    }

    private static (int End, DateOnly Date)? DateAt(List<Token> tokens, int start, DateOnly today, bool allowOn)
    {
        if (Word(tokens, start) is not { } first)
        {
            return null;
        }

        var nextMonday = today.AddDays(8 - IsoDay(today.DayOfWeek));
        switch (first)
        {
            case "today":
                return (start + 1, today);
            case "tomorrow" or "tmrw" or "tmr":
                return (start + 1, today.AddDays(1));
            case "on":
                return allowOn && Word(tokens, start + 1) is { } after && !RelativeDayWords.Contains(after)
                    ? DateAt(tokens, start + 1, today, allowOn: false)
                    : null;
            case "next":
                var second = Word(tokens, start + 1);
                if (second is not null && Weekdays.TryGetValue(second, out var weekday))
                {
                    return (start + 2, nextMonday.AddDays(IsoDay(weekday) - 1));
                }

                return second switch
                {
                    "week" => (start + 2, nextMonday),
                    "month" => (start + 2, new DateOnly(today.Year, today.Month, 1).AddMonths(1)),
                    _ => null,
                };
            case "in":
                var amountWord = Word(tokens, start + 1);
                int? amount = amountWord is "a" or "an" ? 1
                    : amountWord is not null && Count().IsMatch(amountWord) ? int.Parse(amountWord, CultureInfo.InvariantCulture)
                    : null;
                if (amount is not { } n || n < 1)
                {
                    return null;
                }

                return Word(tokens, start + 2) switch
                {
                    "day" or "days" => (start + 3, today.AddDays(n)),
                    "week" or "weeks" => (start + 3, today.AddDays(7 * n)),
                    "month" or "months" => (start + 3, today.AddMonths(n)),
                    _ => null,
                };
        }

        if (Weekdays.TryGetValue(first, out var day))
        {
            var ahead = (IsoDay(day) - IsoDay(today.DayOfWeek) + 7) % 7;
            return (start + 1, today.AddDays(ahead == 0 ? 7 : ahead));
        }

        if (tokens[start].Numeric is not { } numeric)
        {
            return null;
        }

        if (IsoDate().Match(numeric) is { Success: true } iso)
        {
            return DateOf(Number(iso.Groups[1]), Number(iso.Groups[2]), Number(iso.Groups[3])) is { } value ? (start + 1, value) : null;
        }

        if (DottedDate().Match(numeric) is { Success: true } dotted)
        {
            var resolved = dotted.Groups[3].Success
                ? DateOf(Number(dotted.Groups[3]), Number(dotted.Groups[2]), Number(dotted.Groups[1]))
                : Upcoming(Number(dotted.Groups[2]), Number(dotted.Groups[1]), today);
            return resolved is { } value ? (start + 1, value) : null;
        }

        // "25 october", "sep 25", "sep 25th"
        if (Word(tokens, start + 1) is not { } next)
        {
            return null;
        }

        (int Day, int Month)? pair = null;
        if (DayOfMonth().Match(first) is { Success: true } dayFirst && Months.TryGetValue(next, out var monthAfter))
        {
            pair = (Number(dayFirst.Groups[1]), monthAfter);
        }
        else if (Months.TryGetValue(first, out var monthBefore) && DayOfMonth().Match(next) is { Success: true } dayAfter)
        {
            pair = (Number(dayAfter.Groups[1]), monthBefore);
        }

        return pair is { } found && Upcoming(found.Month, found.Day, today) is { } upcoming ? (start + 2, upcoming) : null;
    }

    private static (int End, RepeatPattern Repeat)? RepeatAt(List<Token> tokens, int start)
    {
        switch (Word(tokens, start))
        {
            case "daily":
                return (start + 1, new RepeatPattern(RepeatFrequency.Daily));
            case "weekdays":
                return (start + 1, new RepeatPattern(RepeatFrequency.Weekly, Days: WorkWeek));
            case "weekly":
                return (start + 1, new RepeatPattern(RepeatFrequency.Weekly));
            case "monthly":
                return (start + 1, new RepeatPattern(RepeatFrequency.Monthly));
            case "every":
                break;
            default:
                return null;
        }

        if (Word(tokens, start + 1) is not { } first)
        {
            return null;
        }

        switch (first)
        {
            case "day":
                return (start + 2, new RepeatPattern(RepeatFrequency.Daily));
            case "weekday":
                return (start + 2, new RepeatPattern(RepeatFrequency.Weekly, Days: WorkWeek));
            case "weekend":
                return (start + 2, new RepeatPattern(RepeatFrequency.Weekly, Days: Weekend));
            case "week":
                return (start + 2, new RepeatPattern(RepeatFrequency.Weekly));
            case "month":
                return (start + 2, new RepeatPattern(RepeatFrequency.Monthly));
        }

        if (Count().IsMatch(first))
        {
            var interval = int.Parse(first, CultureInfo.InvariantCulture);
            if (interval < 1)
            {
                return null;
            }

            RepeatFrequency? frequency = Word(tokens, start + 2) switch
            {
                "day" or "days" => RepeatFrequency.Daily,
                "week" or "weeks" => RepeatFrequency.Weekly,
                "month" or "months" => RepeatFrequency.Monthly,
                _ => null,
            };
            return frequency is { } found ? (start + 3, new RepeatPattern(found, interval)) : null;
        }

        if (Ordinal().Match(first) is { Success: true } ordinal)
        {
            var day = Number(ordinal.Groups[1]);
            return day is >= 1 and <= 31 ? (start + 2, new RepeatPattern(RepeatFrequency.Monthly, MonthDay: day)) : null;
        }

        // "every monday", "every mon, wed and fri"
        var days = new HashSet<DayOfWeek>();
        var index = start + 1;
        while (Word(tokens, index) is { } word && Weekdays.TryGetValue(word, out var weekday))
        {
            days.Add(weekday);
            index++;
            if (Word(tokens, index) == "and" && Word(tokens, index + 1) is { } following && Weekdays.ContainsKey(following))
            {
                index++;
            }
        }

        return days.Count == 0 ? null : (index, new RepeatPattern(RepeatFrequency.Weekly, Days: days));
    }

    private static DateOnly? DateOf(int year, int month, int day) =>
        month is >= 1 and <= 12 && year is >= 1 and <= 9999 && day >= 1 && day <= DateTime.DaysInMonth(year, month)
            ? new DateOnly(year, month, day)
            : null;

    /// <summary>A date without a year: this year's, or next year's when this year's has passed.</summary>
    private static DateOnly? Upcoming(int month, int day, DateOnly today) =>
        DateOf(today.Year, month, day) is { } thisYear && thisYear >= today ? thisYear : DateOf(today.Year + 1, month, day);

    private static int IsoDay(DayOfWeek day) => day == DayOfWeek.Sunday ? 7 : (int)day;

    private static int Number(Group group) => int.Parse(group.Value, CultureInfo.InvariantCulture);

    private static Dictionary<string, int> BuildMonths()
    {
        string[] names = ["january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december"];
        var months = new Dictionary<string, int>(StringComparer.Ordinal) { ["sept"] = 9 };
        for (var i = 0; i < names.Length; i++)
        {
            months[names[i]] = i + 1;
            months[names[i][..3]] = i + 1;
        }

        return months;
    }

    [GeneratedRegex(@"^\p{L}[\p{L}\p{N}_-]*\z", RegexOptions.CultureInvariant)]
    private static partial Regex MarkerName();

    [GeneratedRegex(@"^([0-9]{1,2}):([0-9]{2})\z", RegexOptions.CultureInvariant)]
    private static partial Regex Clock24();

    [GeneratedRegex(@"^([0-9]{1,2})(?::([0-9]{2}))?(am|pm)\z", RegexOptions.CultureInvariant)]
    private static partial Regex Clock12();

    [GeneratedRegex(@"^[0-9]{1,2}\z", RegexOptions.CultureInvariant)]
    private static partial Regex HourOnly();

    [GeneratedRegex(@"^([0-9]{4})-([0-9]{2})-([0-9]{2})\z", RegexOptions.CultureInvariant)]
    private static partial Regex IsoDate();

    [GeneratedRegex(@"^([0-9]{1,2})\.([0-9]{1,2})\.([0-9]{4})?\z", RegexOptions.CultureInvariant)]
    private static partial Regex DottedDate();

    [GeneratedRegex(@"^([0-9]{1,2})(st|nd|rd|th)?\z", RegexOptions.CultureInvariant)]
    private static partial Regex DayOfMonth();

    [GeneratedRegex(@"^([0-9]{1,2})(st|nd|rd|th)\z", RegexOptions.CultureInvariant)]
    private static partial Regex Ordinal();

    [GeneratedRegex(@"^[0-9]{1,3}\z", RegexOptions.CultureInvariant)]
    private static partial Regex Count();

    private sealed record Token(string Raw, int Start, int End)
    {
        /// <summary>A leading backslash keeps the word as text.</summary>
        public bool Escaped => Raw.Length > 1 && Raw[0] == '\\';

        public string Text => Escaped ? Raw[1..] : Raw;

        /// <summary>For matching words: lowercase, without trailing punctuation; null when escaped.</summary>
        public string? Word => Escaped ? null : Raw.ToLowerInvariant().TrimEnd(Trailing.ToCharArray());

        /// <summary>For numeric dates, whose dots matter.</summary>
        public string? Numeric => Escaped ? null : Raw.ToLowerInvariant().TrimEnd(TrailingButDot.ToCharArray());
    }

    private sealed record Marker(SpanKind Kind, string Name);

    private sealed record Phrase(SpanKind Kind, int Start, int End, DateOnly? Date = null, TimeOnly? Time = null, RepeatPattern? Repeat = null);
}
