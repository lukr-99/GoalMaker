using System.Globalization;
using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Planning;

/// <summary>
/// Which rituals ran on which planning day, from any device (docs/reminders.md). A run's id comes
/// from the owner, the ritual and the day, so recording it on both devices makes one row.
/// </summary>
public sealed class RitualRunList
{
    public const string PlanTomorrow = "plan_tomorrow";
    private const string Table = "ritual_runs";
    private const string Namespace = "b8c61b22-5e0c-4f0a-9d1e-6f4a2c7e3b91";
    private readonly IReplica replica;
    private readonly NewRows rows;
    private readonly Action requestSync;

    public RitualRunList(IReplica replica, NewRows rows, Action requestSync)
    {
        this.replica = replica;
        this.rows = rows;
        this.requestSync = requestSync;
        replica.Changed += (_, table) =>
        {
            if (table == Table)
            {
                Changed?.Invoke(this, EventArgs.Empty);
            }
        };
    }

    /// <summary>Raised after every change to the runs.</summary>
    public event EventHandler? Changed;

    /// <summary>The id every device gives a run (contracts/vectors/reminders.json).</summary>
    public static string IdOf(string owner, string ritual, DateOnly day) =>
        NameBasedUuid.Of(Namespace, $"{owner.ToLowerInvariant()}/{ritual}/{day.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture)}");

    /// <summary>The planning days on which <paramref name="ritual"/> was done or skipped.</summary>
    public IReadOnlySet<DateOnly> Ran(string ritual) => replica.All(Table)
        .Where(row => (string?)row["ritual"] == ritual && row[SyncedTable.DeletedAt] is null && (string?)row["day"] is not null)
        .Select(row => DateOnly.ParseExact((string)row["day"]!, "yyyy-MM-dd", CultureInfo.InvariantCulture))
        .ToHashSet();

    /// <summary>Records that <paramref name="ritual"/> was done (or, when <paramref name="skipped"/>, skipped) for planning <paramref name="day"/>.</summary>
    public void Record(string ritual, DateOnly day, bool skipped = false)
    {
        if (rows.Owner() is not { } owner)
        {
            return;
        }

        var id = IdOf(owner, ritual, day);
        var outcome = skipped ? "skipped" : "done";
        var row = replica.Get(Table, id);
        if (row is not null)
        {
            row["outcome"] = outcome;
            row[SyncedTable.DeletedAt] = null;
        }
        else
        {
            row = rows.Create(Table, new Dictionary<string, JsonNode?>
            {
                [SyncedTable.Id] = id,
                ["ritual"] = ritual,
                ["day"] = day.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture),
                ["outcome"] = outcome,
            });
            if (row is null)
            {
                return;
            }
        }

        replica.Queue(Table, row);
        requestSync();
    }
}
