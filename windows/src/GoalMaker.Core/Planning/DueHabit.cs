namespace GoalMaker.Core.Planning;

/// <summary>A habit whose reminder a look found due on planning <paramref name="Day"/> (docs/reminders.md).</summary>
public sealed record DueHabit(HabitItem Habit, DateOnly Day);
