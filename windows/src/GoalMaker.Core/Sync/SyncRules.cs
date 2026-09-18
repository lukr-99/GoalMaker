using System.Globalization;
using System.Text.RegularExpressions;

namespace GoalMaker.Core.Sync;

/// <summary>
/// The sync decisions both apps make the same way (docs/sync.md), pinned by
/// contracts/vectors/sync-merge.json.
/// </summary>
public static partial class SyncRules
{
    public static readonly TimeSpan Overlap = TimeSpan.FromSeconds(60);
    public static readonly TimeSpan FullResyncAfter = TimeSpan.FromDays(80);
    private const string OutputFormat = "yyyy-MM-dd'T'HH:mm:ss.ffffff'Z'";

    /// <summary>Merge one pulled row. A pending local change wins, except against a tombstone (delete wins).</summary>
    public static MergeDecision Merge(RowVersion? local, bool pending, RowVersion remote) => (pending, remote.Deleted, local) switch
    {
        (true, true, _) => new MergeDecision(TakeRemote: true, DropPending: true),
        (true, false, _) => new MergeDecision(TakeRemote: false, DropPending: false),
        (false, _, null) => new MergeDecision(TakeRemote: true, DropPending: false),
        _ => new MergeDecision(
            TakeRemote: string.CompareOrdinal(remote.UpdatedAt, local.UpdatedAt) > 0,
            DropPending: false),
    };

    /// <summary>A table starts over when it never synced or its watermark is more than 80 days old.</summary>
    public static bool NeedsFullResync(string? watermark, DateTimeOffset now) =>
        watermark is null || Parse(watermark) is not { } last || now - last > FullResyncAfter;

    /// <summary>Where a pull starts: 60 seconds before the watermark, or from the beginning.</summary>
    public static string? PullFrom(string? watermark) =>
        watermark is not null && Parse(watermark) is { } last ? Format(last - Overlap) : null;

    /// <summary>Server timestamps as UTC text with 6 fractional digits, so text order is time order.</summary>
    public static string? NormalizeTimestamp(string text) => Parse(text) is { } instant ? Format(instant) : null;

    public static string Format(DateTimeOffset instant) =>
        instant.ToUniversalTime().ToString(OutputFormat, CultureInfo.InvariantCulture);

    /// <summary>A stored timestamp read back as an instant, or null when the text isn't one.</summary>
    public static DateTimeOffset? InstantOf(string text) => Parse(text);

    private static DateTimeOffset? Parse(string text)
    {
        if (!Shape().IsMatch(text))
        {
            return null;
        }

        // Postgres writes offsets as +00 or +0000; normalize to +00:00 before parsing.
        var iso = text.Replace(' ', 'T');
        iso = ShortOffset().Replace(iso, "$1:00");
        iso = CompactOffset().Replace(iso, "$1:$2");
        return DateTimeOffset.TryParse(iso, CultureInfo.InvariantCulture, DateTimeStyles.None, out var parsed)
            ? parsed
            : null;
    }

    [GeneratedRegex(@"^[0-9]{4}-[0-9]{2}-[0-9]{2}[T ][0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]{1,9})?(Z|[+-][0-9]{2}(:?[0-9]{2})?)\z", RegexOptions.CultureInvariant)]
    private static partial Regex Shape();

    [GeneratedRegex(@"([+-][0-9]{2})\z", RegexOptions.CultureInvariant)]
    private static partial Regex ShortOffset();

    [GeneratedRegex(@"([+-][0-9]{2})([0-9]{2})\z", RegexOptions.CultureInvariant)]
    private static partial Regex CompactOffset();
}
