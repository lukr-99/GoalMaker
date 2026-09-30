namespace GoalMaker.Core.Planning;

/// <summary>
/// One week of the Tally block in stats (docs/tally.md): the Monday it starts on, its minutes, and
/// each category's minutes, most first.
/// </summary>
public sealed record TallyWeek(DateOnly Start, int Minutes, IReadOnlyList<TallyMinutes> Categories);
