namespace GoalMaker.Core.Planning;

/// <summary>What the Plan tomorrow ritual shows for a task (docs/plan-tomorrow.md).</summary>
public enum PlanDecision
{
    /// <summary>Open and planned today or earlier: still needs a decision.</summary>
    Undecided,
    Tomorrow,

    /// <summary>Planned for a day after tomorrow.</summary>
    Later,

    /// <summary>Open with no day: changed elsewhere, but decided.</summary>
    Unplanned,
    Done,
    Dropped,
}
