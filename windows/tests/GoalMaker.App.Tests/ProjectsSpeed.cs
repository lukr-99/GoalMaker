using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Tests;

/// <summary>Helpers for the Projects speed tests.</summary>
internal static class ProjectsSpeed
{
    /// <summary>Lets the board catch up: the search's pause runs out on the fake clock.</summary>
    public static void Flush(TestPlanner planner, ProjectsViewModel page)
    {
        _ = page;
        planner.Time.Advance(TimeSpan.FromSeconds(1));
    }
}
