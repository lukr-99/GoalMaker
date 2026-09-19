namespace GoalMaker.Core.Planning;

/// <summary>
/// A habit as the screens and rules see it (docs/habits.md). <see cref="Cadence"/> is daily, weekdays,
/// per_week or per_month; <see cref="Weekdays"/> is the weekday bitmask (Monday 1 ... Sunday 64) and
/// <see cref="Times"/> the days a week or month needs. <see cref="Measure"/> is check, count or amount;
/// <see cref="Target"/> and <see cref="Unit"/> belong to a count or an amount.
/// </summary>
public sealed record HabitItem(string Id, string Name, DateOnly StartsOn)
{
    public string Cadence { get; init; } = HabitRules.Daily;

    public int? Weekdays { get; init; }

    public int? Times { get; init; }

    public string Measure { get; init; } = HabitRules.Check;

    public double? Target { get; init; }

    public string? Unit { get; init; }

    public string? Emoji { get; init; }

    public string? GoalId { get; init; }

    public bool Archived { get; init; }

    public double Position { get; init; }

    public bool Deleted { get; init; }
}
