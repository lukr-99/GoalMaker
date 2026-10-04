namespace GoalMaker.Core.Planning;

/// <summary>
/// A picture of a life goal (ADR 0018): the row that syncs. The file is <c>&lt;owner id&gt;/&lt;id&gt;.jpg</c>
/// in the life-goal-pictures bucket and in each device's picture cache.
/// </summary>
public sealed record LifeGoalPicture(
    string Id,
    string LifeGoalId,
    int Width,
    int Height,
    double Position = 0,
    bool Deleted = false);
