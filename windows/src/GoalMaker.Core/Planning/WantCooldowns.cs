namespace GoalMaker.Core.Planning;

/// <summary>
/// The owner's thresholds for a new want's cooldown (docs/wants.md): under <see cref="SmallUnder"/> it
/// waits <see cref="SmallDays"/>, under <see cref="MediumUnder"/> <see cref="MediumDays"/>, anything
/// more <see cref="LargeDays"/>, and without a price or in another currency <see cref="UnpricedDays"/>.
/// One synced row per owner; <see cref="Default"/> without it.
/// </summary>
public sealed record WantCooldowns(
    double SmallUnder,
    int SmallDays,
    double MediumUnder,
    int MediumDays,
    int LargeDays,
    int UnpricedDays,
    string Currency)
{
    /// <summary>What a new owner starts with, pinned by the 'defaults' of contracts/vectors/wants.json.</summary>
    public static readonly WantCooldowns Default = new(1000, 7, 10000, 30, 90, 30, "CZK");
}
