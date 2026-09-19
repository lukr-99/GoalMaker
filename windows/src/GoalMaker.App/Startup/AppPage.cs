namespace GoalMaker.App.Startup;

/// <summary>Pages a launch switch or link can open.</summary>
public enum AppPage
{
    Today,
    Tomorrow,
    Inbox,
    Plan,
    Settings,
    Areas,
    Archive,
    Activity,
    Goals,
    Habits,
    Reviews,

    /// <summary>One period's guided review; opened from the Reviews page.</summary>
    Review,

    /// <summary>A task's details; opened from a list, never by a launch switch.</summary>
    Task,
}
