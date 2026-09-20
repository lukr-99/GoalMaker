namespace GoalMaker.Core.Backup;

/// <summary>
/// The weekly automatic export (spec, story 92): when the next one is due and which old files go.
/// A week is counted from the last one that was written, not from a calendar day, so a PC that was
/// off for three weeks writes one at the next start rather than three.
/// </summary>
public static class WeeklyBackupRules
{
    /// <summary>How long a written export stands before the next one is due.</summary>
    public static readonly TimeSpan Every = TimeSpan.FromDays(7);

    /// <summary>How many files the folder keeps; the oldest beyond this go.</summary>
    public const int Keep = 8;

    /// <summary>Whether one is due: never written, or written a week or more ago.</summary>
    public static bool IsDue(DateTimeOffset? lastWritten, DateTimeOffset now) =>
        lastWritten is not { } last || now - last >= Every;

    /// <summary>
    /// The files to remove so that only <see cref="Keep"/> remain, oldest first. The names are the
    /// exports in the folder; they sort by day because of how they are named (docs/backup.md).
    /// </summary>
    public static IReadOnlyList<string> Prune(IEnumerable<string> files)
    {
        var ordered = files.OrderBy(name => name, StringComparer.Ordinal).ToList();
        return ordered.Count <= Keep ? [] : [.. ordered.Take(ordered.Count - Keep)];
    }
}
