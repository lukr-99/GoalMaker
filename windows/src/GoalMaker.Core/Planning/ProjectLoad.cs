namespace GoalMaker.Core.Planning;

/// <summary>How many items a project's Backlog, To do and Doing hold, which the project list shows.</summary>
public sealed record ProjectLoad(int Backlog, int Todo, int Doing)
{
    public static ProjectLoad None { get; } = new(0, 0, 0);

    /// <summary>Everything still open on the board: Backlog, To do and Doing together.</summary>
    public int Open => Backlog + Todo + Doing;
}
