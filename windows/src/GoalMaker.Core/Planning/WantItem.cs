namespace GoalMaker.Core.Planning;

/// <summary>
/// A want as the Wants page shows it (docs/wants.md): what, why, what it costs, and the cooldown it
/// waits out before it is decided. <see cref="CheckedPrice"/> is the last price Claude found.
/// </summary>
public sealed record WantItem(
    string Id,
    string Title,
    string Reason,
    int CooldownDays,
    DateOnly AddedOn,
    DateOnly CoolsUntil,
    string? Link = null,
    double? Price = null,
    string Currency = "CZK",
    string? AreaId = null,
    string? Decision = null,
    string? DecidedAt = null,
    string DecisionNote = "",
    double? CheckedPrice = null,
    string? CheckedAt = null,
    string CheckedNote = "",
    string MadeBy = ProjectRules.Owner,
    bool Deleted = false);
