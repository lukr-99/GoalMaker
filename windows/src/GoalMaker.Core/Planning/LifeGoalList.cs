using System.Globalization;
using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Planning;

/// <summary>
/// The owner's life goals and their pictures' rows, read from the replica and changed through its
/// outbox (docs/life-goals.md). The picture files are not here (ADR 0018). Every write asks for a sync.
/// </summary>
public sealed class LifeGoalList
{
    private const string Table = "life_goals";
    private const string Pictures = "life_goal_pictures";
    private const int MaxTitle = 200;
    private const int MaxWhy = 2000;
    private const int MaxSide = 4096;
    private readonly IReplica replica;
    private readonly NewRows rows;
    private readonly Action requestSync;

    public LifeGoalList(IReplica replica, NewRows rows, Action requestSync)
    {
        this.replica = replica;
        this.rows = rows;
        this.requestSync = requestSync;
        replica.Changed += (_, table) =>
        {
            if (table is Table or Pictures)
            {
                Changed?.Invoke(this, EventArgs.Empty);
            }
        };
    }

    /// <summary>Raised after every change to a life goal or a picture.</summary>
    public event EventHandler? Changed;

    /// <summary>Every life goal that isn't deleted, in the page's order (<see cref="LifeGoalRules.Ordered"/>).</summary>
    public IReadOnlyList<LifeGoalItem> All() => LifeGoalRules.Ordered(Live(Table).Select(ToItem));

    public LifeGoalItem? Get(string id) =>
        replica.Get(Table, id) is { } row && ToItem(row) is { Deleted: false } goal ? goal : null;

    /// <summary>The pictures of <paramref name="lifeGoalId"/> that aren't deleted, in their order.</summary>
    public IReadOnlyList<LifeGoalPicture> PicturesOf(string lifeGoalId) =>
        [.. AllPictures().Where(picture => picture.LifeGoalId == lifeGoalId)];

    /// <summary>Every picture that isn't deleted, by life goal and then in its order.</summary>
    public IReadOnlyList<LifeGoalPicture> AllPictures() =>
        [.. Live(Pictures).Select(ToPicture)
            .OrderBy(picture => picture.LifeGoalId, StringComparer.Ordinal)
            .ThenBy(picture => picture.Position)
            .ThenBy(picture => picture.Id, StringComparer.Ordinal)];

    /// <summary>The deleted pictures' rows still in the replica, each with when it was deleted.</summary>
    public IReadOnlyDictionary<string, string> PictureTombstones() => replica.All(Pictures)
        .Where(row => (string?)row[SyncedTable.DeletedAt] is not null)
        .ToDictionary(row => (string?)row[SyncedTable.Id] ?? string.Empty, row => (string)row[SyncedTable.DeletedAt]!, StringComparer.Ordinal);

    /// <summary>Adds an open life goal after the others. Null without a title or a why.</summary>
    public LifeGoalItem? Add(LifeGoalDraft draft)
    {
        if (Check(draft) is not { } clean)
        {
            return null;
        }

        var positions = Live(Table).Select(row => Number(row["position"]) ?? 0).ToList();
        var values = Values(clean);
        values["status"] = LifeGoalRules.Open;
        values["position"] = positions.Count == 0 ? 0 : positions.Max() + 1;
        values["made_by"] = ProjectRules.Owner;
        if (rows.Create(Table, values) is not { } row)
        {
            return null;
        }

        Queue(Table, row);
        return ToItem(row);
    }

    public bool Update(string id, LifeGoalDraft draft) => Check(draft) is { } clean && Change(Table, id, row =>
    {
        foreach (var (column, value) in Values(clean))
        {
            row[column] = value;
        }
    });

    public bool Achieve(string id) => Close(id, LifeGoalRules.Achieved);

    public bool Drop(string id) => Close(id, LifeGoalRules.Dropped);

    public bool Reopen(string id) => Change(Table, id, row =>
    {
        row["status"] = LifeGoalRules.Open;
        row["closed_at"] = null;
    });

    /// <summary>Puts the open life goals in the order of <paramref name="ids"/>; one not named keeps its place after them.</summary>
    public bool Reorder(IReadOnlyList<string> ids) => SetOrder(Table, Live(Table), ids);

    /// <summary>Deletes a life goal with its pictures' rows, all with one time so <see cref="Restore"/> can bring them back together.</summary>
    public bool Delete(string id)
    {
        if (Live(Table).FirstOrDefault(row => (string?)row[SyncedTable.Id] == id) is not { } goal)
        {
            return false;
        }

        var at = rows.Timestamp();
        foreach (var picture in Live(Pictures).Where(row => (string?)row["life_goal_id"] == id))
        {
            picture[SyncedTable.DeletedAt] = at;
            replica.Queue(Pictures, picture);
        }

        goal[SyncedTable.DeletedAt] = at;
        Queue(Table, goal);
        return true;
    }

    /// <summary>Brings a deleted life goal back with the pictures deleted along with it, as the undo after a delete does.</summary>
    public bool Restore(string id)
    {
        if (replica.Get(Table, id) is not { } goal || (string?)goal[SyncedTable.DeletedAt] is not { } deletedAt)
        {
            return false;
        }

        foreach (var picture in replica.All(Pictures)
            .Where(row => (string?)row["life_goal_id"] == id && (string?)row[SyncedTable.DeletedAt] == deletedAt))
        {
            picture[SyncedTable.DeletedAt] = null;
            replica.Queue(Pictures, picture);
        }

        goal[SyncedTable.DeletedAt] = null;
        Queue(Table, goal);
        return true;
    }

    /// <summary>
    /// Adds a picture's row after the life goal's others, for a file of <paramref name="width"/> by
    /// <paramref name="height"/> pixels; the caller keeps the file under the returned id. Null when the
    /// life goal is gone or the size is not real.
    /// </summary>
    public LifeGoalPicture? AddPicture(string lifeGoalId, int width, int height)
    {
        if (Get(lifeGoalId) is null || width is < 1 or > MaxSide || height is < 1 or > MaxSide)
        {
            return null;
        }

        var positions = PicturesOf(lifeGoalId).Select(picture => picture.Position).ToList();
        var values = new Dictionary<string, JsonNode?>
        {
            ["life_goal_id"] = lifeGoalId,
            ["position"] = positions.Count == 0 ? 0 : positions.Max() + 1,
            ["width"] = width,
            ["height"] = height,
        };
        if (rows.Create(Pictures, values) is not { } row)
        {
            return null;
        }

        Queue(Pictures, row);
        return ToPicture(row);
    }

    public bool RemovePicture(string id) => Change(Pictures, id, row => row[SyncedTable.DeletedAt] = rows.Timestamp());

    /// <summary>Puts <paramref name="lifeGoalId"/>'s pictures in the order of <paramref name="ids"/>.</summary>
    public bool ReorderPictures(string lifeGoalId, IReadOnlyList<string> ids) =>
        SetOrder(Pictures, Live(Pictures).Where(row => (string?)row["life_goal_id"] == lifeGoalId), ids);

    private static LifeGoalDraft? Check(LifeGoalDraft draft)
    {
        var title = Clip(draft.Title, MaxTitle);
        var why = Clip(draft.Why, MaxWhy);
        return title.Length == 0 || why.Length == 0 ? null : draft with { Title = title, Why = why };
    }

    private static string Clip(string? text, int length)
    {
        var trimmed = text?.Trim() ?? string.Empty;
        return trimmed.Length > length ? trimmed[..length] : trimmed;
    }

    private static Dictionary<string, JsonNode?> Values(LifeGoalDraft draft) => new()
    {
        ["title"] = draft.Title,
        ["why"] = draft.Why,
        ["by_date"] = draft.By?.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture),
        ["area_id"] = draft.AreaId,
    };

    private static LifeGoalItem ToItem(JsonObject row) => new(
        (string?)row[SyncedTable.Id] ?? string.Empty,
        (string?)row["title"] ?? string.Empty,
        (string?)row["why"] ?? string.Empty,
        By: (string?)row["by_date"] is { } by ? DateOnly.ParseExact(by, "yyyy-MM-dd", CultureInfo.InvariantCulture) : null,
        AreaId: (string?)row["area_id"],
        Status: (string?)row["status"] ?? LifeGoalRules.Open,
        ClosedAt: (string?)row["closed_at"],
        Position: Number(row["position"]) ?? 0,
        CreatedAt: (string?)row[SyncedTable.CreatedAt] ?? string.Empty,
        MadeBy: (string?)row["made_by"] ?? ProjectRules.Owner,
        Deleted: row[SyncedTable.DeletedAt] is not null);

    private static LifeGoalPicture ToPicture(JsonObject row) => new(
        (string?)row[SyncedTable.Id] ?? string.Empty,
        (string?)row["life_goal_id"] ?? string.Empty,
        (int)(Number(row["width"]) ?? 0),
        (int)(Number(row["height"]) ?? 0),
        Number(row["position"]) ?? 0,
        row[SyncedTable.DeletedAt] is not null);

    private static double? Number(JsonNode? node) => node is JsonValue value
        ? value.TryGetValue<double>(out var number) ? number
            : value.TryGetValue<long>(out var whole) ? whole
            : value.TryGetValue<int>(out var small) ? small
            : null
        : null;

    private IEnumerable<JsonObject> Live(string table) => replica.All(table).Where(row => row[SyncedTable.DeletedAt] is null);

    private bool SetOrder(string table, IEnumerable<JsonObject> candidates, IReadOnlyList<string> ids)
    {
        var byId = candidates.ToDictionary(row => (string?)row[SyncedTable.Id] ?? string.Empty);
        if (ids.Any(id => !byId.ContainsKey(id)))
        {
            return false;
        }

        for (var index = 0; index < ids.Count; index++)
        {
            var row = byId[ids[index]];
            if (Number(row["position"]) != index)
            {
                row["position"] = (double)index;
                replica.Queue(table, row);
            }
        }

        requestSync();
        return true;
    }

    private bool Close(string id, string status) => Change(Table, id, row =>
    {
        row["status"] = status;
        row["closed_at"] = rows.Timestamp();
    });

    private bool Change(string table, string id, Action<JsonObject> edit)
    {
        if (replica.Get(table, id) is not { } row || row[SyncedTable.DeletedAt] is not null)
        {
            return false;
        }

        edit(row);
        Queue(table, row);
        return true;
    }

    private void Queue(string table, JsonObject row)
    {
        replica.Queue(table, row);
        requestSync();
    }
}
