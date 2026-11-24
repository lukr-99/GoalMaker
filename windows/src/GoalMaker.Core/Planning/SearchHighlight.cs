namespace GoalMaker.Core.Planning;

/// <summary>
/// Where the words of a search are in a piece of text, ignoring case and accents as the search does
/// (docs/projects.md, "Searching items"): the parts to light up in a result, in order and not
/// overlapping. "kun" lights up "kůň" in "Žluťoučký kůň".
/// </summary>
public static class SearchHighlight
{
    /// <summary>The matched parts of <paramref name="text"/> as start and length, for the folded <paramref name="words"/>.</summary>
    public static IReadOnlyList<(int Start, int Length)> Ranges(string text, IReadOnlyList<string> words)
    {
        if (text.Length == 0 || words.Count == 0)
        {
            return [];
        }

        // Fold the text a character at a time, remembering which character each folded one came from.
        var folded = new System.Text.StringBuilder(text.Length);
        var from = new List<int>(text.Length);
        for (var i = 0; i < text.Length; i++)
        {
            // Half of a surrogate pair (an emoji) can't be normalized alone, and has no accent to drop.
            foreach (var character in char.IsSurrogate(text[i]) ? text[i].ToString() : ArchiveRules.Fold(text[i].ToString()))
            {
                folded.Append(character);
                from.Add(i);
            }
        }

        var lit = new bool[text.Length];
        var haystack = folded.ToString();
        foreach (var word in words.Where(word => word.Length > 0))
        {
            for (var at = haystack.IndexOf(word, StringComparison.Ordinal); at >= 0; at = haystack.IndexOf(word, at + word.Length, StringComparison.Ordinal))
            {
                for (var j = at; j < at + word.Length; j++)
                {
                    lit[from[j]] = true;
                }
            }
        }

        var ranges = new List<(int, int)>();
        for (var i = 0; i < lit.Length; i++)
        {
            if (!lit[i])
            {
                continue;
            }

            var start = i;
            while (i < lit.Length && lit[i])
            {
                i++;
            }

            ranges.Add((start, i - start));
        }

        return ranges;
    }
}
