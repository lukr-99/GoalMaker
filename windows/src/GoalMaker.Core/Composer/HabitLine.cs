namespace GoalMaker.Core.Composer;

/// <summary>
/// What the Habits bar reads from a typed line (docs/composer.md): the name, the cadence (with its
/// weekday mask or times), the measure (with its target and unit) and the direction (at_most for a
/// limit), in the ids of HabitRules.
/// </summary>
public sealed record HabitLine(string Name, string Cadence, int? Weekdays, int? Times, string Measure, double? Target, string? Unit, string Direction);
