using System.Globalization;

namespace GoalMaker.Core.Planning;

/// <summary>
/// Life goals (docs/life-goals.md), pinned by contracts/vectors/life-goals.json, which the Android app
/// and the connector run too: how far a by date is, and the order of the page.
/// </summary>
public static class LifeGoalRules
{
    public const string Open = "open";
    public const string Achieved = "achieved";
    public const string Dropped = "dropped";

    /// <summary>The years the editor offers for the by date.</summary>
    public static IReadOnlyList<int> ByYears { get; } = [5, 10, 20];

    /// <summary>How far <paramref name="by"/> is from the planning day <paramref name="today"/>; null without a by date.</summary>
    public static TimeLeft? TimeLeft(DateOnly? by, DateOnly today)
    {
        if (by is not { } day)
        {
            return null;
        }

        if (day < today)
        {
            return new TimeLeft(TimeLeftUnit.Past, 0);
        }

        if (day == today)
        {
            return new TimeLeft(TimeLeftUnit.Today, 0);
        }

        var months = (day.Year * 12 + day.Month) - (today.Year * 12 + today.Month);
        if (day.Day < today.Day)
        {
            months--;
        }

        return months switch
        {
            >= 12 => new TimeLeft(TimeLeftUnit.Years, months / 12),
            >= 1 => new TimeLeft(TimeLeftUnit.Months, months),
            _ => new TimeLeft(TimeLeftUnit.Days, day.DayNumber - today.DayNumber),
        };
    }

    /// <summary>Open ones in the owner's order, then achieved and dropped ones, the most recently closed first.</summary>
    public static IReadOnlyList<LifeGoalItem> Ordered(IEnumerable<LifeGoalItem> goals)
    {
        var all = goals.ToList();
        var open = all.Where(goal => goal.Status == Open)
            .OrderBy(goal => goal.Position)
            .ThenBy(goal => Instant(goal.CreatedAt))
            .ThenBy(goal => goal.Id, StringComparer.Ordinal);
        var closed = all.Where(goal => goal.Status != Open)
            .OrderByDescending(goal => Instant(goal.ClosedAt))
            .ThenBy(goal => goal.Id, StringComparer.Ordinal);
        return [.. open, .. closed];
    }

    /// <summary>The open life goals in the owner's order, the ones the why reminder goes through.</summary>
    public static IReadOnlyList<LifeGoalItem> OpenOnes(IEnumerable<LifeGoalItem> goals) =>
        Ordered(goals.Where(goal => !goal.Deleted && goal.Status == Open));

    private static DateTimeOffset Instant(string? text) => string.IsNullOrEmpty(text)
        ? DateTimeOffset.UnixEpoch
        : DateTimeOffset.Parse(text, CultureInfo.InvariantCulture, DateTimeStyles.AssumeUniversal);
}
