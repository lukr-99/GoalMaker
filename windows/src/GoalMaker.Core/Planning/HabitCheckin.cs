namespace GoalMaker.Core.Planning;

/// <summary>A day's one check-in on a habit: the day's value, or its period skipped (docs/habits.md).</summary>
public sealed record HabitCheckin(string Id, string HabitId, DateOnly Day, double Value, bool Skipped = false, bool Deleted = false);
