namespace GoalMaker.Core.Planning;

/// <summary>Days a habit rests, <see cref="From"/> to <see cref="Until"/> (null while the pause lasts): they neither break its streak nor count.</summary>
public sealed record HabitPause(string Id, string HabitId, DateOnly From, DateOnly? Until = null, bool Deleted = false);
