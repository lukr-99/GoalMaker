using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Planning;

/// <summary>
/// The owner's areas, read from the replica and created through its outbox. M2-11 adds renaming,
/// colors, emoji, order and archiving: an archived area keeps its tasks but leaves the pickers and
/// filters, and naming it again (the composer's @Area) brings it back.
/// </summary>
public sealed class AreaList
{
    private const string Table = "areas";
    private const string Tasks = "tasks";
    private const string ArchivedAt = "archived_at";
    private const int MaxName = 60;
    private const int MaxEmoji = 16;
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

    /// <summary>The areas in use, for pickers and filters: <see cref="All"/> without the archived ones.</summary>
    public IReadOnlyList<AreaItem> Active() => [.. All().Where(area => !area.Archived)];

    /// <summary>The area with this name, ignoring case and surrounding spaces.</summary>
    public AreaItem? Find(string name) => All().FirstOrDefault(area => Key(area.Name) == Key(name));

    /// <summary>The area with this name, brought back if archived, or created (with the next unused palette color) when there is none.</summary>
    public AreaItem? FindOrCreate(string name)
    {
        if (Find(name) is not { } found)
        {
            return Create(name);
        }

        if (found.Archived)
        {
            Restore(found.Id);
        }

        return found with { Archived = false };
    }

    /// <summary>Hides an area from the pickers and filters; its tasks keep it.</summary>
    public bool Archive(string id) => Change(id, row => row[ArchivedAt] = rows.Timestamp());

    /// <summary>Brings an archived area back into the pickers and filters.</summary>
    public bool Restore(string id) => Change(id, row => row[ArchivedAt] = null);

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

    /// <summary>The palette's color ids in order, for the color picker.</summary>
    public IReadOnlyList<string> Palette() => paletteIds;

    /// <summary>Renames an area. False when the name is blank or another area already has it (ignoring case).</summary>
    public bool Rename(string id, string name)
    {
        var trimmed = name.Trim();
        trimmed = trimmed.Length > MaxName ? trimmed[..MaxName] : trimmed;
        return trimmed.Length > 0 && !(Find(trimmed) is { } clash && clash.Id != id) && Change(id, row => row["name"] = trimmed);
    }

    /// <summary>Gives an area another color from the palette. False for a color the palette doesn't have.</summary>
    public bool Recolor(string id, string colorId) => paletteIds.Contains(colorId) && Change(id, row => row["color"] = colorId);

    /// <summary>Sets the area's emoji, or takes it away when <paramref name="emoji"/> is blank.</summary>
    public bool SetEmoji(string id, string? emoji)
    {
        var trimmed = emoji?.Trim();
        trimmed = string.IsNullOrEmpty(trimmed) ? null : trimmed.Length > MaxEmoji ? trimmed[..MaxEmoji] : trimmed;
        return Change(id, row => row["emoji"] = trimmed);
    }

    /// <summary>
    /// Moves an area in use to <paramref name="index"/> among the areas in use, the order the pickers
    /// use, and numbers them all again; archived areas keep their order after them.
    /// </summary>
    public void Move(string id, int index)
    {
        var all = All();
        var order = all.Where(area => !area.Archived).ToList();
        var at = order.FindIndex(area => area.Id == id);
        if (at < 0)
        {
            return;
        }

        var moved = order[at];
        order.RemoveAt(at);
        order.Insert(Math.Clamp(index, 0, order.Count), moved);
        order.AddRange(all.Where(area => area.Archived));
        replica.InTransaction(() =>
        {
            for (var position = 0; position < order.Count; position++)
            {
                if (replica.Get(Table, order[position].Id) is { } row)
                {
                    row["position"] = (double)position;
                    replica.Queue(Table, row);
                }
            }
        });
        requestSync();
    }

    /// <summary>Deletes an area. Its tasks stay and lose the area, so one without a day goes back to the Inbox.</summary>
    public void Delete(string id)
    {
        var stamp = rows.Timestamp();
        replica.InTransaction(() =>
        {
            if (replica.Get(Table, id) is not { } row)
            {
                return;
            }

            row[SyncedTable.DeletedAt] = stamp;
            replica.Queue(Table, row);
            foreach (var task in replica.All(Tasks).Where(task => (string?)task["area_id"] == id))
            {
                task["area_id"] = null;
                replica.Queue(Tasks, task);
            }
        });
        requestSync();
    }

    private static string Key(string name) => name.Trim().ToLowerInvariant();

    private bool Change(string id, Action<JsonObject> edit)
    {
        if (replica.Get(Table, id) is not { } row || row[SyncedTable.DeletedAt] is not null)
        {
            return false;
        }

        edit(row);
        replica.Queue(Table, row);
        requestSync();
        return true;
    }

    private static AreaItem ToItem(JsonObject row) => new(
        (string)row[SyncedTable.Id]!,
        (string?)row["name"] ?? string.Empty,
        (string?)row["color"] ?? string.Empty,
        (string?)row["emoji"],
        row[ArchivedAt] is not null);
}
