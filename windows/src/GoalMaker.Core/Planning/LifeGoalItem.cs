namespace GoalMaker.Core.Planning;

/// <summary>
/// A life goal as the Life goals page shows it (docs/life-goals.md): what the owner wants in their
/// life, <see cref="Why"/> it matters, and the day they mean to have it <see cref="By"/>.
/// <see cref="Status"/> is open, achieved or dropped, and <see cref="ClosedAt"/> says when it stopped being open.
/// </summary>
public sealed record LifeGoalItem(
    string Id,
    string Title,
    string Why,
    DateOnly? By = null,
    string? AreaId = null,
    string Status = LifeGoalRules.Open,
    string? ClosedAt = null,
    double Position = 0,
    string CreatedAt = "",
    string MadeBy = ProjectRules.Owner,
    bool Deleted = false);
