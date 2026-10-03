namespace GoalMaker.Core.Planning;

/// <summary>One clock hour of a planning day on this device: its seconds and each category's, most first.</summary>
public sealed record TallyHour(int Hour, int Seconds, IReadOnlyList<TallySeconds> Categories);
