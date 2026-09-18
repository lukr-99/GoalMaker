using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Planning;

/// <summary>
/// The owner's areas, read from the replica and created through its outbox. M2-11 adds renaming,
/// colors, emoji and order.
/// </summary>
public sealed class AreaList
{
    private const string Table = "areas";
    private const int MaxName = 60;
    private readonly IReplica replica;
    private readonly NewRows rows;
    private readonly IReadOnlyList<string> paletteIds;
    private readonly Action requestSync;

    /// <param name="paletteIds">The area palette's color ids in order (themes.json), for new areas.</param>
    public AreaList(IReplica replica, NewRows rows, IReadOnlyList<string> paletteIds, Action requestSync)
    {
        this.replica = replica;
        this.rows = rows;
        this.paletteIds = paletteIds;
        this.requestSync = requestSync;
        replica.Changed += (_, table) =>
        {
            if (table == Table)
            {
                Changed?.Invoke(this, EventArgs.Empty);
            }
        };
    }

    public event EventHandler? Changed;

    public IReadOnlyList<AreaItem> All() =>
        [.. replica.All(Table)
            .Where(row => row[SyncedTable.DeletedAt] is null)
            .OrderBy(row => (double?)row["position"] ?? 0)
            .ThenBy(row => ((string?)row["name"] ?? string.Empty).ToLowerInvariant(), StringComparer.Ordinal)
            .Select(ToItem)];

    /// <summary>The area with this name, ignoring case and surrounding spaces.</summary>
    public AreaItem? Find(string name) => All().FirstOrDefault(area => Key(area.Name) == Key(name));

    /// <summary>The area with this name, created (with the next unused palette color) when there is none.</summary>
    public AreaItem? FindOrCreate(string name) => Find(name) ?? Create(name);

    public AreaItem? Create(string name)
    {
        var trimmed = name.Trim();
        trimmed = trimmed.Length > MaxName ? trimmed[..MaxName] : trimmed;
        if (trimmed.Length == 0)
        {
            return null;
        }

        var existing = All();
        var used = existing.Select(area => area.ColorId).ToHashSet(StringComparer.Ordinal);
        var color = paletteIds.FirstOrDefault(id => !used.Contains(id)) ?? paletteIds[existing.Count % paletteIds.Count];
        var row = rows.Create(Table, new Dictionary<string, JsonNode?>
        {
            ["name"] = trimmed,
            ["color"] = color,
            ["position"] = (double)existing.Count,
        });
        if (row is null)
        {
            return null;
        }

        replica.Queue(Table, row);
        requestSync();
        return ToItem(row);
    }

    private static string Key(string name) => name.Trim().ToLowerInvariant();

    private static AreaItem ToItem(JsonObject row) => new(
        (string)row[SyncedTable.Id]!,
        (string?)row["name"] ?? string.Empty,
        (string?)row["color"] ?? string.Empty,
        (string?)row["emoji"]);
}
