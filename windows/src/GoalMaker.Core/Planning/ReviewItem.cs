namespace GoalMaker.Core.Planning;

/// <summary>
/// A review of one period (docs/reviews.md): the owner's <see cref="Mood"/> and <see cref="Energy"/>
/// from 1 to 5, the <see cref="Summary"/> a Claude routine may write, and the prompts it asked with
/// their answers.
/// </summary>
public sealed record ReviewItem(string Id, string Kind, DateOnly PeriodStart)
{
    public int? Mood { get; init; }

    public int? Energy { get; init; }

    public string Summary { get; init; } = string.Empty;

    public IReadOnlyList<Reflection> Reflections { get; init; } = [];

    public bool Deleted { get; init; }

    /// <summary>Whether anything was written: it is worth keeping and worth showing among the past reviews.</summary>
    public bool Written =>
        Mood is not null || Energy is not null || !string.IsNullOrWhiteSpace(Summary) || Reflections.Any(reflection => !string.IsNullOrWhiteSpace(reflection.Answer));
}
