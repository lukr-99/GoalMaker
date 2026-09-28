using System.Globalization;

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

    /// <summary>The id of <paramref name="owner"/>'s <paramref name="kind"/> review for the period starting on <paramref name="periodStart"/>.</summary>
    public static string IdOf(string owner, string kind, DateOnly periodStart) =>
        NameBasedUuid.Of(Namespace, $"review/{owner.ToLowerInvariant()}/{kind}/{periodStart.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture)}");
}
