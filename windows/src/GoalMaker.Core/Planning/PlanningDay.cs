namespace GoalMaker.Core.Planning;

/// <summary>
/// The day plans belong to: the local date shifted back by the hour the day starts (docs/lists.md),
/// so planning at 01:30 still happens on yesterday's day.
/// </summary>
public static class PlanningDay
{
    public const int DefaultStartHour = 4;
    public const int LatestStartHour = 6;

    public static DateOnly Of(DateTime now, int startHour = DefaultStartHour) => DateOnly.FromDateTime(now.AddHours(-startHour));
}
