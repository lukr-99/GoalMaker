using System.Globalization;

namespace GoalMaker.Core.About;

/// <summary>
/// The letters on the author card's badge: the first letter of the first and the last word of a name,
/// upper case (contracts/vectors/about.json). One word gives one letter; a blank name gives none.
/// </summary>
public static class Monogram
{
    public static string Of(string name)
    {
        var words = name.Split((char[]?)null, StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
        if (words.Length == 0)
        {
            return "";
        }

        var picked = words.Length == 1 ? words : [words[0], words[^1]];
        return string.Concat(picked.Select(FirstLetter)).ToUpperInvariant();
    }

    private static string FirstLetter(string word) => StringInfo.GetNextTextElement(word, 0);
}
