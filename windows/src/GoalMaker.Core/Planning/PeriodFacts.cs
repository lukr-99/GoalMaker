namespace GoalMaker.Core.Planning;

/// <summary>
/// What a review period looked like, as the reactive prompts read it (docs/reviews.md): the tasks done
/// against the <see cref="AverageDone"/> of the periods before it, the goals with where they stand
/// against where they should be, the habits with the periods they missed, and the tasks that kept moving.
/// </summary>
public sealed record PeriodFacts
{
    public int DoneTasks { get; init; }

    public double AverageDone { get; init; }

    public IReadOnlyList<GoalFact> Goals { get; init; } = [];

    public IReadOnlyList<HabitFact> Habits { get; init; } = [];

    public IReadOnlyList<TaskFact> Tasks { get; init; } = [];

    /// <summary>A goal's progress (<paramref name="Fraction"/>, 0 to 1) against the expected share of its period gone by.</summary>
    public sealed record GoalFact(string Title, double Fraction, double Expected);

    /// <summary>A habit's missed periods out of the ones it had, and the streak it is on now.</summary>
    public sealed record HabitFact(string Name, int Missed, int Periods, int Streak);

    /// <summary>A task and how many times it was moved to another day.</summary>
    public sealed record TaskFact(string Title, int Moves);
}
