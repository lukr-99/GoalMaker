namespace GoalMaker.App.Shell;

/// <summary>What the owner did with a reminder toast (docs/reminders.md).</summary>
public enum ToastAction
{
    /// <summary>Clicked the toast itself, which opens GoalMaker and counts as dismissing it.</summary>
    Open,

    Done,

    Snooze,

    /// <summary>Closed the toast.</summary>
    Dismiss,

    /// <summary>The evening reminder's Plan button or body: opens the Plan tomorrow ritual.</summary>
    Plan,

    /// <summary>The evening reminder's "Not today": quiet for the rest of the planning day.</summary>
    SkipPlan,
}
