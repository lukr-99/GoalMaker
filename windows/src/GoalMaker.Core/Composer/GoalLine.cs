using GoalMaker.Core.Planning;

namespace GoalMaker.Core.Composer;

/// <summary>
/// What the Goals bar reads from a typed line (docs/composer.md): the title (a target stays in it),
/// the period and the progress mode, with the target and unit of a number goal.
/// </summary>
public sealed record GoalLine(string Title, GoalHorizon Horizon, DateOnly PeriodStart, string Mode, double? Target, string? Unit);
