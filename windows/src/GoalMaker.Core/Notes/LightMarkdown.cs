using System.Text;

namespace GoalMaker.Core.Notes;

/// <summary>
/// The light Markdown task notes and letters use (docs/archive.md, contracts/vectors/markdown.json):
/// lines, headings starting with "# " to "### ", list lines starting with "- " or "* ", **bold**,
/// *italic*, and bare http(s) links. Anything unmatched stays as it was typed.
/// </summary>
public static class LightMarkdown
{
    private const string Trailing = ".,;:!?)";
    private const int MaxHeading = 3;
    private static readonly string[] Schemes = ["https://", "http://"];

    public static IReadOnlyList<MarkdownBlock> Parse(string text)
    {
        if (text.Length == 0)
        {
            return [];
        }

        return [.. text.Replace("\r", string.Empty, StringComparison.Ordinal).Split('\n').Select(line =>
        {
            var start = line.TrimStart();
            var level = HeadingLevel(start);
            if (level > 0)
            {
                return new MarkdownBlock(false, Inline(start[(level + 1)..].Trim()), level);
            }

            return start.StartsWith("- ", StringComparison.Ordinal) || start.StartsWith("* ", StringComparison.Ordinal)
                ? new MarkdownBlock(true, Inline(start[2..]))
                : new MarkdownBlock(false, Inline(line));
        })];
    }

    // "# " to "### ": the level, or 0 when the line isn't a heading.
    private static int HeadingLevel(string line)
    {
        var hashes = line.TakeWhile(character => character == '#').Count();
        return hashes is >= 1 and <= MaxHeading && line.Length > hashes && line[hashes] == ' ' ? hashes : 0;
    }

    private static List<MarkdownSpan> Inline(string line)
    {
        var spans = new List<MarkdownSpan>();
        var plain = new StringBuilder();
        void Flush()
        {
            if (plain.Length > 0)
            {
                spans.Add(new MarkdownSpan(plain.ToString()));
            }

            plain.Clear();
        }

        var index = 0;
        while (index < line.Length)
        {
            if (string.CompareOrdinal(line, index, "**", 0, 2) == 0)
            {
                var end = line.IndexOf("**", index + 2, StringComparison.Ordinal);
                if (end > index + 2)
                {
                    Flush();
                    spans.Add(new MarkdownSpan(line[(index + 2)..end], Bold: true));
                    index = end + 2;
                }
                else
                {
                    plain.Append("**");
                    index += 2;
                }

                continue;
            }

            if (line[index] == '*')
            {
                var end = line.IndexOf('*', index + 1);
                if (end > index + 1 && line[index + 1] != ' ' && line[end - 1] != ' ')
                {
                    Flush();
                    spans.Add(new MarkdownSpan(line[(index + 1)..end], Italic: true));
                    index = end + 1;
                }
                else
                {
                    plain.Append('*');
                    index += 1;
                }

                continue;
            }

            if (Array.Find(Schemes, scheme => string.CompareOrdinal(line, index, scheme, 0, scheme.Length) == 0) is { } found)
            {
                var end = index;
                while (end < line.Length && !char.IsWhiteSpace(line[end]))
                {
                    end++;
                }

                var url = line[index..end];
                var cut = url.Length;
                while (cut > 0 && Trailing.Contains(url[cut - 1], StringComparison.Ordinal))
                {
                    cut--;
                }

                if (cut > found.Length)
                {
                    Flush();
                    spans.Add(new MarkdownSpan(url[..cut], Link: url[..cut]));
                    plain.Append(url[cut..]);
                }
                else
                {
                    plain.Append(url);
                }

                index = end;
                continue;
            }

            plain.Append(line[index]);
            index += 1;
        }

        Flush();
        return spans;
    }
}
