namespace GoalMaker.Core.Planning;

/// <summary>
/// What the owner types for a want (docs/wants.md). <see cref="PickedDays"/> overrides the cooldown
/// the price would give; it only counts when the want is added. A <see cref="Kind"/> of need skips
/// the cooldown, need not say why, and may have a day it is <see cref="NeedBy"/>.
/// </summary>
public sealed record WantDraft(
    string Title,
    string Reason,
    string? Link = null,
    double? Price = null,
    string Currency = "CZK",
    string? AreaId = null,
    int? PickedDays = null,
    string Kind = WantRules.Want,
    DateOnly? NeedBy = null);
