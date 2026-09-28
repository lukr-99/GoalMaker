namespace GoalMaker.Core.Planning;

/// <summary>The wants notification to show: the planning day it belongs to and the wants it names, in order.</summary>
public sealed record WantsDue(DateOnly Day, IReadOnlyList<string> WantIds)
{
    public bool Equals(WantsDue? other) => other is not null && Day == other.Day && WantIds.SequenceEqual(other.WantIds);

    public override int GetHashCode() => HashCode.Combine(Day, WantIds.Count);
}
