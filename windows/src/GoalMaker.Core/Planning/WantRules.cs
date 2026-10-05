using System.Globalization;

namespace GoalMaker.Core.Planning;

/// <summary>
/// Wants and their cooldowns (docs/wants.md), pinned by contracts/vectors/wants.json, which the
/// Android app and the connector run too: the cooldown a new want gets from its price, the day it
/// cools, where it stands on a planning day, which wants a day's notification names, and the stats.
/// </summary>
public static class WantRules
{
    public const string Bought = "bought";
    public const string Dropped = "dropped";

    /// <summary>What a row is (supabase/migrations/0025_wants_needs.sql): a want to wait out, or a need to buy.</summary>
    public const string Want = "want";
    public const string Need = "need";

    /// <summary>The composer command that opens the Wants page with a new want (<c>/want Trail shoes</c>).</summary>
    public const string Command = "want";

    /// <summary>The most days a picked cooldown can be.</summary>
    public const int MaxDays = 365;

    private const string Namespace = "b8c61b22-5e0c-4f0a-9d1e-6f4a2c7e3b91";

    /// <summary>The id of an owner's one row of thresholds, the same on every device.</summary>
    public static string CooldownsId(string owner) =>
        NameBasedUuid.Of(Namespace, "want-cooldowns/" + owner.ToLowerInvariant());

    /// <summary>The days a new want waits: none for a need, <paramref name="picked"/> when the owner chose, otherwise by its price.</summary>
    public static int CooldownDays(double? price, string currency, WantCooldowns cooldowns, int? picked = null, string kind = Want)
    {
        if (kind == Need)
        {
            return 0;
        }

        if (picked is { } days)
        {
            return Math.Clamp(days, 0, MaxDays);
        }

        if (price is not { } amount || currency != cooldowns.Currency)
        {
            return cooldowns.UnpricedDays;
        }

        return amount < cooldowns.SmallUnder ? cooldowns.SmallDays
            : amount < cooldowns.MediumUnder ? cooldowns.MediumDays
            : cooldowns.LargeDays;
    }

    public static DateOnly CoolsUntil(DateOnly addedOn, int days) => addedOn.AddDays(days);

    /// <summary>Where a want stands on the planning day <paramref name="today"/>; null for a deleted want.</summary>
    public static WantState? State(WantItem want, DateOnly today) =>
        want.Deleted ? null
        : want.Decision is not null ? WantState.Decided
        : today >= want.CoolsUntil ? WantState.Ready
        : WantState.Cooling;

    /// <summary>How far the cooldown has run on <paramref name="today"/>, 0 to 1, for the ring.</summary>
    public static double Progress(WantItem want, DateOnly today)
    {
        if (want.Decision is not null)
        {
            return 1;
        }

        var total = want.CoolsUntil.DayNumber - want.AddedOn.DayNumber;
        if (total <= 0)
        {
            return 1;
        }

        return Math.Clamp((double)(today.DayNumber - want.AddedOn.DayNumber) / total, 0, 1);
    }

    /// <summary>
    /// The wants a notification on <paramref name="today"/> names: undecided ones that became ready after
    /// <paramref name="lastNotified"/> and by today, or only today's when there was none before; oldest first, then by title.
    /// </summary>
    public static IReadOnlyList<WantItem> Ready(IEnumerable<WantItem> wants, DateOnly? lastNotified, DateOnly today) =>
    [
        .. wants
            .Where(want => !want.Deleted && want.Kind != Need && want.Decision is null && want.CoolsUntil <= today
                && (lastNotified is { } last ? want.CoolsUntil > last : want.CoolsUntil == today))
            .OrderBy(want => want.CoolsUntil)
            .ThenBy(want => want.Title.ToLower(CultureInfo.InvariantCulture), StringComparer.Ordinal),
    ];

    /// <summary>Bought and dropped, and the dropped prices in <paramref name="currency"/> added up; deleted wants never count.</summary>
    public static WantStats Stats(IEnumerable<WantItem> wants, string currency)
    {
        var kept = wants.Where(want => !want.Deleted && want.Kind != Need).ToList();
        var dropped = kept.Where(want => want.Decision == Dropped).ToList();
        return new WantStats(
            kept.Count(want => want.Decision == Bought),
            dropped.Count,
            dropped.Where(want => want.Currency == currency).Sum(want => want.Price ?? 0));
    }

    /// <summary>The open needs: by the day they are needed by (none last), then when they were added, then by title.</summary>
    public static IReadOnlyList<WantItem> Needs(IEnumerable<WantItem> wants) =>
    [
        .. wants
            .Where(want => !want.Deleted && want.Kind == Need && want.Decision is null)
            .OrderBy(want => want.NeedBy is null)
            .ThenBy(want => want.NeedBy)
            .ThenBy(want => want.AddedOn)
            .ThenBy(want => want.Title.ToLower(CultureInfo.InvariantCulture), StringComparer.Ordinal),
    ];

    /// <summary>Whether an open need's day passed before the planning day <paramref name="today"/>.</summary>
    public static bool NeedLate(WantItem want, DateOnly today) =>
        want.Kind == Need && want.Decision is null && want.NeedBy is { } day && day < today;
}
