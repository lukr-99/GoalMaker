using System.Globalization;
using System.Text;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Planning;

/// <summary>The archive of done tasks (docs/archive.md, contracts/vectors/archive.json).</summary>
public static class ArchiveRules
{
    /// <summary>
    /// The done tasks that match <paramref name="query"/>, the most recently completed first (ties by
    /// id). Every word of the query has to be found in the title or the notes, ignoring case and accents.
    /// </summary>
    public static IReadOnlyList<TaskItem> Search(IEnumerable<TaskItem> tasks, string query)
    {
        var words = query.Split(' ').Select(Fold).Where(word => word.Length > 0).ToList();
        return [.. tasks
            .Where(task => !task.Deleted && task.State == TaskState.Done)
            .Where(task =>
            {
                var text = Fold(task.Title) + "\n" + Fold(task.Notes);
                return words.TrueForAll(word => text.Contains(word, StringComparison.Ordinal));
            })
            .OrderByDescending(task => task.CompletedAt is { } stamp ? SyncRules.InstantOf(stamp) : null)
            .ThenBy(task => task.Id, StringComparer.Ordinal)];
    }

    /// <summary>Text as the search compares it: without accents, in lower case.</summary>
    public static string Fold(string text)
    {
        var decomposed = text.Normalize(NormalizationForm.FormD);
        var kept = new StringBuilder(decomposed.Length);
        foreach (var character in decomposed)
        {
            if (CharUnicodeInfo.GetUnicodeCategory(character) is not (UnicodeCategory.NonSpacingMark or UnicodeCategory.SpacingCombiningMark or UnicodeCategory.EnclosingMark))
            {
                kept.Append(character);
            }
        }

        return kept.ToString().ToLowerInvariant();
    }
}
