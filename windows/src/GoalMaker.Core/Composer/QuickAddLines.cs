using System.Globalization;
using System.Text.RegularExpressions;
using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Composer;

/// <summary>
/// What the bottom bar on Wants, Habits and Goals reads from a typed line (docs/composer.md, "Adding on
/// Wants, Habits and Goals"), pinned by contracts/vectors/quick-add.json. A line is read word by word:
/// a word is what sits between spaces, compared without case and without the punctuation at its end.
/// The first phrase of each kind counts; anything not understood stays in the title.
/// </summary>
public static partial class QuickAddLines
{
    /// <summary>The longest wait a want can pick, as in the want form.</summary>
    public const int MaxWaitDays = 365;

    private const int Free = 0;
    private const int Used = 1;
    private const int Kept = 2;

    private static readonly Dictionary<string, string> Currencies = new(StringComparer.Ordinal)
    {
        ["kč"] = "CZK",
        ["kc"] = "CZK",
        ["czk"] = "CZK",
        ["€"] = "EUR",
        ["eur"] = "EUR",
        ["euro"] = "EUR",
        ["euros"] = "EUR",
        ["$"] = "USD",
        ["usd"] = "USD",
        ["£"] = "GBP",
        ["gbp"] = "GBP",
    };

    private static readonly string[] Signs = ["€", "$", "£"];

    private static readonly Dictionary<string, int> WaitUnits = new(StringComparer.Ordinal)
    {
        ["day"] = 1,
        ["days"] = 1,
        ["week"] = 7,
        ["weeks"] = 7,
        ["month"] = 30,
        ["months"] = 30,
    };

    private static readonly string[] Per = ["a", "per", "each", "every"];

    private static readonly Dictionary<string, int> DayNames = new(StringComparer.Ordinal)
    {
        ["mon"] = 1,
        ["monday"] = 1,
        ["tue"] = 2,
        ["tues"] = 2,
        ["tuesday"] = 2,
        ["wed"] = 4,
        ["wednesday"] = 4,
        ["thu"] = 8,
        ["thur"] = 8,
        ["thurs"] = 8,
        ["thursday"] = 8,
        ["fri"] = 16,
        ["friday"] = 16,
        ["sat"] = 32,
        ["saturday"] = 32,
        ["sun"] = 64,
        ["sunday"] = 64,
    };

    private const int WorkWeek = 31;
    private const int Weekend = 96;

    private static readonly Dictionary<string, int> Months = new(StringComparer.Ordinal)
    {
        ["january"] = 1,
        ["jan"] = 1,
        ["february"] = 2,
        ["feb"] = 2,
        ["march"] = 3,
        ["mar"] = 3,
        ["april"] = 4,
        ["apr"] = 4,
        ["may"] = 5,
        ["june"] = 6,
        ["jun"] = 6,
        ["july"] = 7,
        ["jul"] = 7,
        ["august"] = 8,
        ["aug"] = 8,
        ["september"] = 9,
        ["sep"] = 9,
        ["sept"] = 9,
        ["october"] = 10,
        ["oct"] = 10,
        ["november"] = 11,
        ["nov"] = 11,
        ["december"] = 12,
        ["dec"] = 12,
    };

    // Small words that never name a unit, so "2 of my friends" is no amount.
    private static readonly HashSet<string> NotUnits = new(StringComparer.Ordinal)
    {
        "a", "an", "the", "of", "and", "or", "to", "in", "on", "at", "for", "per", "each", "every", "x",
        "day", "days", "week", "weeks", "month", "months", "year", "years",
    };

    // Units a habit measures as an amount (asked for its value); any other unit is a count (+1 a tap).
    private static readonly HashSet<string> AmountUnits = new(StringComparer.Ordinal)
    {
        "min", "mins", "minute", "minutes", "h", "hr", "hrs", "hour", "hours", "s", "sec", "secs", "second", "seconds",
        "km", "kms", "mi", "mile", "miles", "m", "meter", "meters", "metre", "metres", "step", "steps", "page", "pages",
        "ml", "l", "liter", "liters", "litre", "litres", "kcal", "cal", "kg", "g",
    };

    /// <summary>A want: "Kindle 3290 Kč wait 2 weeks because I read on the train".</summary>
    public static WantLine ReadWant(string line)
    {
        var all = Words(line);
        var cut = all.FindIndex(word => word.Key == "because");
        var reason = cut < 0 ? string.Empty : string.Join(' ', all.Skip(cut + 1).Select(word => word.Text));
        var read = new Reader(cut < 0 ? all : all.Take(cut).ToList());
        double? price = null;
        string? currency = null;
        int? waitDays = null;

        for (var i = 0; i < read.Count && price is null; i++)
        {
            if (read.Key(i) is not { } key)
            {
                continue;
            }

            var sign = Signs.FirstOrDefault(key.StartsWith);
            var joined = JoinedPrice().Match(key);
            if (sign is not null && ParseNumber(key[sign.Length..]) is { } signed)
            {
                (price, currency) = (signed.Value, Currencies[sign]);
                read.Mark(i, i + 1, Used);
            }
            else if (joined.Success && Currencies.TryGetValue(joined.Groups[2].Value, out var code) && ParseNumber(joined.Groups[1].Value) is { } value)
            {
                (price, currency) = (value.Value, code);
                read.Mark(i, i + 1, Used);
            }
            else if (read.Number(i) is { } number && read.Key(number.End) is { } after && Currencies.TryGetValue(after, out var named))
            {
                (price, currency) = (number.Value, named);
                read.Mark(i, number.End + 1, Used);
            }
        }

        for (var i = 0; i < read.Count && waitDays is null; i++)
        {
            if (read.Key(i) != "wait")
            {
                continue;
            }

            var j = i + 1;
            if (read.Key(j) == "for")
            {
                j++;
            }

            int? n = read.Key(j) is { } amount
                ? Whole().IsMatch(amount) && int.TryParse(amount, NumberStyles.None, CultureInfo.InvariantCulture, out var whole) ? whole
                : amount is "a" or "an" or "one" ? 1 : null
                : null;
            if (n is not { } count || !WaitUnits.TryGetValue(read.Key(j + 1) ?? string.Empty, out var per))
            {
                continue;
            }

            var days = (long)count * per;
            if (days is >= 1 and <= MaxWaitDays)
            {
                waitDays = (int)days;
                read.Mark(i, j + 2, Used);
            }
            else
            {
                read.Mark(i, j + 2, Kept);
            }
        }

        return new WantLine(read.Rest(), reason.Length > 0 ? reason : null, price, currency, waitDays);
    }

    /// <summary>A habit: "Swim 2 times a week", "Read 20 minutes every day", "Piano every mon and thu".</summary>
    public static HabitLine ReadHabit(string line)
    {
        var read = new Reader(Words(line));
        var cadence = HabitRules.Daily;
        int? weekdays = null;
        int? times = null;
        var measure = HabitRules.Check;
        double? target = null;
        string? unit = null;

        for (var i = 0; i < read.Count; i++)
        {
            if (CadenceAt(read, i) is not { } found)
            {
                continue;
            }

            if (!found.Fits)
            {
                read.Mark(i, found.End, Kept);
                continue;
            }

            (cadence, weekdays, times) = (found.Cadence, found.Weekdays, found.Times);
            read.Mark(i, found.End, Used);
            break;
        }

        for (var i = 0; i < read.Count; i++)
        {
            if (read.Key(i) is not { } key)
            {
                continue;
            }

            (int N, int End)? counted = Whole().IsMatch(key) && int.TryParse(key, NumberStyles.None, CultureInfo.InvariantCulture, out var n) && n >= 1 && read.Key(i + 1) is "times" or "time"
                ? (n, i + 2)
                : key is "once" or "twice" && read.PerDay(i + 1) > 0 ? (key == "once" ? 1 : 2, i + 1) : null;
            if (counted is { } run)
            {
                measure = HabitRules.Count;
                target = run.N;
                read.Mark(i, run.End + read.PerDay(run.End), Used);
                break;
            }

            if (read.Measure(i) is not { Value: > 0 } amount)
            {
                continue;
            }

            measure = amount.Decimal || AmountUnits.Contains(amount.Unit.ToLowerInvariant()) ? HabitRules.Amount : HabitRules.Count;
            target = amount.Value;
            unit = amount.Unit;
            read.Mark(i, amount.End + read.PerDay(amount.End), Used);
            break;
        }

        return new HabitLine(read.Rest(), cadence, weekdays, times, measure, target, unit);
    }

    /// <summary>A goal: "Run 30 km this week", "Read 3 books in November". Its period is read against <paramref name="today"/>, the planning day.</summary>
    public static GoalLine ReadGoal(string line, DateOnly today)
    {
        var read = new Reader(Words(line));
        var horizon = GoalHorizon.Week;
        var start = GoalRules.PeriodStart(GoalHorizon.Week, today);
        static DateOnly Next(GoalHorizon h, DateOnly day) => GoalRules.PeriodStart(h, GoalRules.PeriodEnd(h, GoalRules.PeriodStart(h, day)).AddDays(1));

        for (var i = 0; i < read.Count; i++)
        {
            if (read.Key(i) is not { } key)
            {
                continue;
            }

            var second = read.Key(i + 1) ?? string.Empty;
            GoalHorizon? named = second switch
            {
                "year" => GoalHorizon.Year,
                "month" => GoalHorizon.Month,
                "week" => GoalHorizon.Week,
                _ => null,
            };
            (GoalHorizon Horizon, DateOnly Start, int Length)? found = null;
            if (key is "this" or "next" && named is { } h)
            {
                found = (h, key == "this" ? GoalRules.PeriodStart(h, today) : Next(h, today), 2);
            }
            else if (key == "today")
            {
                found = (GoalHorizon.Day, today, 1);
            }
            else if (key == "tomorrow")
            {
                found = (GoalHorizon.Day, today.AddDays(1), 1);
            }
            else if (key == "in" && Months.TryGetValue(second, out var month))
            {
                found = (GoalHorizon.Month, new DateOnly(month < today.Month ? today.Year + 1 : today.Year, month, 1), 2);
            }
            else if (key == "in" && Year().IsMatch(second))
            {
                var year = int.Parse(second, CultureInfo.InvariantCulture);
                if (year < today.Year)
                {
                    read.Mark(i, i + 2, Kept);
                    continue;
                }

                found = (GoalHorizon.Year, new DateOnly(year, 1, 1), 2);
            }

            if (found is not { } period)
            {
                continue;
            }

            (horizon, start) = (period.Horizon, period.Start);
            read.Mark(i, i + period.Length, Used);
            break;
        }

        double? target = null;
        string? unit = null;
        for (var i = 0; i < read.Count && target is null; i++)
        {
            if (read.Measure(i) is { Value: > 0 } amount)
            {
                (target, unit) = (amount.Value, amount.Unit);
            }
        }

        return new GoalLine(read.Rest(), horizon, start, target is null ? GoalRules.ModeDone : GoalRules.ModeNumber, target, unit);
    }

    private static (string Cadence, int? Weekdays, int? Times, int End, bool Fits)? CadenceAt(Reader read, int i)
    {
        if (read.Key(i) is not { } key)
        {
            return null;
        }

        string K(int offset) => read.Key(i + offset) ?? string.Empty;
        static bool Period(string word) => word is "week" or "month";
        static (string, int?, int?, int, bool) PerPeriod(int n, string period, int end) => period == "week"
            ? (HabitRules.PerWeek, null, n, end, n is >= 1 and <= 7)
            : (HabitRules.PerMonth, null, n, end, n is >= 1 and <= 31);

        var nx = TimesJoined().Match(key);
        if (Whole().IsMatch(key) && K(1) is "x" or "times" or "time" && Per.Contains(K(2)) && Period(K(3)))
        {
            return PerPeriod(ParseWhole(key), K(3), i + 4);
        }

        if (nx.Success && Per.Contains(K(1)) && Period(K(2)))
        {
            return PerPeriod(ParseWhole(nx.Groups[1].Value), K(2), i + 3);
        }

        if (key is "once" or "twice" && Per.Contains(K(1)) && Period(K(2)))
        {
            return PerPeriod(key == "once" ? 1 : 2, K(2), i + 3);
        }

        if (key is "weekly" or "monthly")
        {
            return PerPeriod(1, key == "weekly" ? "week" : "month", i + 1);
        }

        if (key == "daily")
        {
            return (HabitRules.Daily, null, null, i + 1, true);
        }

        if (key is "every" or "each" && K(1) == "day")
        {
            return (HabitRules.Daily, null, null, i + 2, true);
        }

        static (string, int?, int?, int, bool) Days(int mask, int end) => (HabitRules.OnWeekdays, mask, null, end, true);
        if (key == "weekdays")
        {
            return Days(WorkWeek, i + 1);
        }

        if (key == "weekends")
        {
            return Days(Weekend, i + 1);
        }

        if (key is not ("every" or "on"))
        {
            return null;
        }

        if (K(1) is "weekday" or "weekdays")
        {
            return Days(WorkWeek, i + 2);
        }

        if (K(1) is "weekend" or "weekends")
        {
            return Days(Weekend, i + 2);
        }

        if (!DayNames.TryGetValue(K(1), out var mask))
        {
            return null;
        }

        var j = i + 1;
        while (true)
        {
            if (DayNames.ContainsKey(read.Key(j + 1) ?? string.Empty))
            {
                j += 1;
            }
            else if (read.Key(j + 1) is "and" or "&" or "" && DayNames.ContainsKey(read.Key(j + 2) ?? string.Empty))
            {
                j += 2;
            }
            else
            {
                break;
            }

            mask |= DayNames[read.Key(j)!];
        }

        return Days(mask, j + 1);
    }

    private static int ParseWhole(string digits) =>
        int.TryParse(digits, NumberStyles.None, CultureInfo.InvariantCulture, out var n) ? n : int.MaxValue;

    private static List<Word> Words(string line) =>
        [.. line.Split((char[]?)null, StringSplitOptions.RemoveEmptyEntries).Select(text => new Word(text, text.ToLowerInvariant().TrimEnd(".,;:!?".ToCharArray())))];

    // A number as written: one or two decimals after a dot or comma, or groups of three after one.
    private static (double Value, bool Decimal)? ParseNumber(string text)
    {
        if (DecimalNumber().IsMatch(text))
        {
            return (double.Parse(text.Replace(',', '.'), NumberStyles.AllowDecimalPoint, CultureInfo.InvariantCulture), text.Contains('.') || text.Contains(','));
        }

        return GroupedNumber().IsMatch(text)
            ? (double.Parse(text.Replace(".", string.Empty).Replace(",", string.Empty), NumberStyles.None, CultureInfo.InvariantCulture), false)
            : null;
    }

    [GeneratedRegex("^[0-9]+$", RegexOptions.CultureInvariant)]
    private static partial Regex Whole();

    [GeneratedRegex("^[0-9]+([.,][0-9]{1,2})?$", RegexOptions.CultureInvariant)]
    private static partial Regex DecimalNumber();

    [GeneratedRegex("^[0-9]{1,3}([.,][0-9]{3})+$", RegexOptions.CultureInvariant)]
    private static partial Regex GroupedNumber();

    [GeneratedRegex("^[0-9]{1,3}$", RegexOptions.CultureInvariant)]
    private static partial Regex GroupHead();

    [GeneratedRegex("^[0-9]{3}$", RegexOptions.CultureInvariant)]
    private static partial Regex Group();

    [GeneratedRegex(@"^([0-9][0-9.,]*)(\p{L}+)$", RegexOptions.CultureInvariant)]
    private static partial Regex Joined();

    [GeneratedRegex(@"^([0-9][0-9.,]*)(\p{L}+|[€$£])$", RegexOptions.CultureInvariant)]
    private static partial Regex JoinedPrice();

    [GeneratedRegex(@"^\p{L}[\p{L}-]*$", RegexOptions.CultureInvariant)]
    private static partial Regex UnitWord();

    [GeneratedRegex("^([0-9]+)x$", RegexOptions.CultureInvariant)]
    private static partial Regex TimesJoined();

    [GeneratedRegex("^[0-9]{4}$", RegexOptions.CultureInvariant)]
    private static partial Regex Year();

    private sealed record Word(string Text, string Key);

    // Walks the words of a line, remembering which were read (used) and which were kept as text.
    private sealed class Reader(List<Word> words)
    {
        private readonly int[] state = new int[words.Count];

        public int Count => words.Count;

        public string? Key(int i) => i < words.Count && state[i] == Free ? words[i].Key : null;

        public void Mark(int from, int to, int value)
        {
            for (var i = from; i < to; i++)
            {
                state[i] = value;
            }
        }

        public string Rest() => string.Join(' ', words.Where((_, i) => state[i] != Used).Select(word => word.Text));

        // A number from word i, maybe across words ("10 000"); End is the word after it.
        public (double Value, bool Decimal, int End)? Number(int i)
        {
            if (Key(i) is not { } head)
            {
                return null;
            }

            if (GroupHead().IsMatch(head))
            {
                var end = i + 1;
                var digits = head;
                while (Key(end) is { } next && Group().IsMatch(next))
                {
                    digits += next;
                    end++;
                }

                if (end > i + 1)
                {
                    return (double.Parse(digits, NumberStyles.None, CultureInfo.InvariantCulture), false, end);
                }
            }

            return ParseNumber(head) is { } one ? (one.Value, one.Decimal, i + 1) : null;
        }

        // A unit word at i: letters (a hyphen inside is fine), and not one of the small words.
        public string? Unit(int i)
        {
            if (Key(i) is null)
            {
                return null;
            }

            var text = words[i].Text.TrimEnd(".,;:!?".ToCharArray());
            return UnitWord().IsMatch(text) && text.Length <= 20 && !NotUnits.Contains(text.ToLowerInvariant()) ? text : null;
        }

        // A number and its unit from word i, apart ("30 min") or together ("30min").
        public (double Value, bool Decimal, string Unit, int End)? Measure(int i)
        {
            if (Number(i) is { } number)
            {
                return Unit(number.End) is { } unit ? (number.Value, number.Decimal, unit, number.End + 1) : null;
            }

            if (Key(i) is null)
            {
                return null;
            }

            var joined = Joined().Match(words[i].Text.TrimEnd(".,;:!?".ToCharArray()));
            if (!joined.Success || ParseNumber(joined.Groups[1].Value) is not { } value || NotUnits.Contains(joined.Groups[2].Value.ToLowerInvariant()))
            {
                return null;
            }

            return (value.Value, value.Decimal, joined.Groups[2].Value, i + 1);
        }

        // "a day", "per day" or "each day" at i: how many words, or 0.
        public int PerDay(int i) => Per.Contains(Key(i) ?? string.Empty) && Key(i + 1) == "day" ? 2 : 0;
    }
}
