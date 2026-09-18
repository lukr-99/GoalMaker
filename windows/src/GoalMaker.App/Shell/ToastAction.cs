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
}
