using System.Globalization;
using GoalMaker.Core.Notes;

namespace GoalMaker.Core.Planning;

/// <summary>
/// Which period a review covers and the id every writer gives it (supabase/migrations/0008_reviews.sql,
/// contracts/vectors/reviews.json), so a review written on the phone, the PC or through the connector
/// is one row.
/// </summary>
public static class ReviewRules
{
    public const string Weekly = "weekly";
    public const string Monthly = "monthly";
    public const string Yearly = "yearly";
    private const string Namespace = "b8c61b22-5e0c-4f0a-9d1e-6f4a2c7e3b91";

    /// <summary>The start of the <paramref name="kind"/> period <paramref name="day"/> falls in: its week's Monday, its month's first day, or January 1.</summary>
    public static DateOnly PeriodStart(string kind, DateOnly day) => kind switch
    {
        Weekly => day.AddDays(-(((int)day.DayOfWeek + 6) % 7)),
        Monthly => new DateOnly(day.Year, day.Month, 1),
        Yearly => new DateOnly(day.Year, 1, 1),
        _ => throw new ArgumentException($"Unknown review kind: {kind}", nameof(kind)),
    };

    /// <summary>
    /// Whether the <paramref name="kind"/> review reminder ringing on <paramref name="day"/> can say the
    /// letter is here: the review of the period it looks back on has a summary a Claude routine wrote
    /// (docs/letter.md, 'letterWaiting' in contracts/vectors/reminders.json). The reminder reads
    /// <paramref name="reviews"/> as they are when it rings.
    /// </summary>
    public static bool LetterWaiting(string kind, DateOnly day, IEnumerable<ReviewItem> reviews)
    {
        var start = ReviewReminder.PeriodStart(kind, day);
        return reviews.Any(review =>
            !review.Deleted && review.Kind == kind && review.PeriodStart == start && !string.IsNullOrWhiteSpace(review.Summary));
    }

    /// <summary>
    /// The line of a letter the Reviews list shows: its first line of text that isn't a heading, or its
    /// first heading when it has nothing else, without the Markdown marks. Null when there is no letter.
    /// </summary>
    public static string? LetterPreview(string summary)
    {
        var lines = LightMarkdown.Parse(summary)
            .Select(block => (Block: block, Text: string.Concat(block.Spans.Select(span => span.Text)).Trim()))
            .Where(line => line.Text.Length > 0)
            .ToList();
        var chosen = lines.FirstOrDefault(line => line.Block.Heading == 0);
        return chosen.Block is not null ? chosen.Text : lines.Select(line => line.Text).FirstOrDefault();
    }

    /// <summary>
    /// The January nudge for planning day <paramref name="today"/> (spec story 66, 'newYear' in
    /// contracts/vectors/reviews.json): null outside January, once the owner dismissed it this year
    /// (<paramref name="dismissedYear"/> is the year it was last dismissed), or when last year is reviewed
    /// and this year has goals. <paramref name="yearGoals"/> counts this year's year goals that are neither
    /// deleted nor dropped.
    /// </summary>
    public static NewYearNudge? NewYear(DateOnly today, int yearGoals, bool lastYearReviewed, int? dismissedYear)
    {
        if (today.Month != 1 || dismissedYear == today.Year)
        {
            return null;
        }

        var nudge = new NewYearNudge(today.Year, !lastYearReviewed, yearGoals == 0);
        return nudge.Review || nudge.Goals ? nudge : null;
    }

    /// <summary>
    /// <see cref="NewYear"/> from what the owner has: this year's year goals that are neither deleted nor
    /// dropped, and whether last year's yearly review was written.
    /// </summary>
    public static NewYearNudge? NewYearFor(DateOnly today, IEnumerable<GoalItem> goals, IEnumerable<ReviewItem> reviews, int? dismissedYear)
    {
        var thisYear = new DateOnly(today.Year, 1, 1);
        var yearGoals = goals.Count(goal => !goal.Deleted && goal.Horizon == GoalHorizon.Year && goal.PeriodStart == thisYear && goal.Status != GoalRules.Dropped);
        var reviewed = reviews.Any(review => !review.Deleted && review.Kind == Yearly && review.PeriodStart == thisYear.AddYears(-1) && review.Written);
        return NewYear(today, yearGoals, reviewed, dismissedYear);
    }

    /// <summary>The id of <paramref name="owner"/>'s <paramref name="kind"/> review for the period starting on <paramref name="periodStart"/>.</summary>
    public static string IdOf(string owner, string kind, DateOnly periodStart) =>
        NameBasedUuid.Of(Namespace, $"review/{owner.ToLowerInvariant()}/{kind}/{periodStart.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture)}");
}
