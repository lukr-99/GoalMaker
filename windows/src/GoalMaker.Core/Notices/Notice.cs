using GoalMaker.Core.Problems;

namespace GoalMaker.Core.Notices;

/// <summary>
/// Something the owner can act on, shown as an icon on the sidebar's Settings item and as a line in
/// the banner at the top of Settings (docs/problems.md, "What the owner sees").
/// </summary>
public abstract record Notice
{
    private Notice()
    {
    }

    /// <summary>The last check found <paramref name="Version"/>, and it is not installed yet.</summary>
    public sealed record UpdateReady(string Version) : Notice;

    /// <summary>Something went wrong while nobody was watching.</summary>
    public sealed record ProblemWaiting(Problem Problem) : Notice;
}
