namespace GoalMaker.Core.Planning;

/// <summary>
/// The January nudge (spec story 66, docs/reviews.md): the year that has begun, and what is still to do
/// for it: look back on the year before (<paramref name="Review"/>) and set this year's goals
/// (<paramref name="Goals"/>).
/// </summary>
public sealed record NewYearNudge(int Year, bool Review, bool Goals);
