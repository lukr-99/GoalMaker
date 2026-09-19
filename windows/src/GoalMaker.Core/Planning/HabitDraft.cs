namespace GoalMaker.Core.Planning;

/// <summary>
/// A habit as the owner typed it, before <see cref="HabitList"/> cleans it: the fields that don't fit
/// the cadence or the measure are dropped there (docs/habits.md).
/// </summary>
public sealed record HabitDraft(string Name, DateOnly StartsOn)
{
    public string Cadence { get; init; } = HabitRules.Daily;

    public int? Weekdays { get; init; }

    public int? Times { get; init; }

    public string Measure { get; init; } = HabitRules.Check;

    public double? Target { get; init; }

    public string? Unit { get; init; }

    public string? Emoji { get; init; }

    public string? GoalId { get; init; }
}
