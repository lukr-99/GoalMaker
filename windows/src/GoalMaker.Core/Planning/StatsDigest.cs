namespace GoalMaker.Core.Planning;

/// <summary>
/// What the stats screen shows (docs/stats.md, spec stories 64 and 67): tasks finished week by week,
/// goals hit month by month, how each habit is holding up, and the mood and energy of past reviews.
/// </summary>
public sealed record StatsDigest
{
    public IReadOnlyList<Week> Weeks { get; init; } = [];

    public IReadOnlyList<Month> Months { get; init; } = [];

    public IReadOnlyList<Habit> Habits { get; init; } = [];

    public IReadOnlyList<Rating> Ratings { get; init; } = [];

    /// <summary>Tasks finished over the weeks on show.</summary>
    public int Done => Weeks.Sum(week => week.Done);

    /// <summary>What that works out at a week.</summary>
    public double PerWeek => Weeks.Count == 0 ? 0 : (double)Done / Weeks.Count;

    /// <summary>The fullest week, the earliest of them when two tie, or null when nothing was finished.</summary>
    public Week? BestWeek => Weeks.Where(week => week.Done > 0).OrderByDescending(week => week.Done).ThenBy(week => week.Start).FirstOrDefault();

    public int GoalsHit => Months.Sum(month => month.Hit);

    public int GoalsTotal => Months.Sum(month => month.Total);

    /// <summary>Habit periods met against the ones that asked for something, 0 to 1.</summary>
    public double HabitRate
    {
        get
        {
            var periods = Habits.Sum(habit => habit.Periods);
            return periods == 0 ? 0 : (double)Habits.Sum(habit => habit.Met) / periods;
        }
    }

    /// <summary>Whether there is nothing to show yet, so the screen can say so instead of drawing empty charts.</summary>
    public bool Empty => Done == 0 && GoalsTotal == 0 && Habits.Count == 0 && Ratings.Count == 0;

    /// <summary>One column of the tasks chart: the Monday it starts on and what was finished that week.</summary>
    public sealed record Week(DateOnly Start, int Done);

    /// <summary>One column of the goals chart: the first of the month, the goals it held and how many were hit.</summary>
    public sealed record Month(DateOnly Start, int Hit, int Total)
    {
        public double Fraction => Total == 0 ? 0 : (double)Hit / Total;
    }

    /// <summary>A habit over the window: periods met, the run going now and the longest run inside the window.</summary>
    public sealed record Habit(string Id, string Name, string? Emoji, int Met, int Periods, int Streak, int Best)
    {
        public double Rate => Periods == 0 ? 0 : (double)Met / Periods;
    }

    /// <summary>One review's ratings, for the mood and energy chart (spec story 64).</summary>
    public sealed record Rating(DateOnly PeriodStart, int? Mood, int? Energy);
}
