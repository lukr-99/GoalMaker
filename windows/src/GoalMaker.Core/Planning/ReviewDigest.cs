namespace GoalMaker.Core.Planning;

/// <summary>
/// The look back a review opens with (docs/reviews.md): how much was done against the period before,
/// the day and the area that carried it, the goals and habits of the period, the tasks still open, and
/// the facts the reactive prompts read.
/// </summary>
public sealed record ReviewDigest
{
    public string Kind { get; init; } = ReviewRules.Weekly;

    public DateOnly PeriodStart { get; init; }

    public DateOnly PeriodEnd { get; init; }

    public int Done { get; init; }

    public int DoneBefore { get; init; }

    public IReadOnlyList<Day> Days { get; init; } = [];

    public Day? BestDay { get; init; }

    public Area? StrongestArea { get; init; }

    public IReadOnlyList<Goal> Goals { get; init; } = [];

    public IReadOnlyList<Habit> Habits { get; init; } = [];

    public IReadOnlyList<TaskItem> OpenTasks { get; init; } = [];

    public PeriodFacts Facts { get; init; } = new();

    /// <summary>How the period compares with the one before: the difference in tasks done.</summary>
    public int Change => Done - DoneBefore;

    public sealed record Day(DateOnly Date, int Done);

    public sealed record Area(string Name, int Done);

    public sealed record Goal(string Id, string Title, string? Emoji, double Fraction, bool Hit);

    public sealed record Habit(string Id, string Name, string? Emoji, int Met, int Periods, int Streak);
}
