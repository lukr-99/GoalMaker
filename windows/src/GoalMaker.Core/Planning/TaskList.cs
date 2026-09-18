using System.Globalization;
using System.Text.Json.Nodes;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Planning;

/// <summary>
/// Tasks as the lists need them: read from the replica, written through its outbox. Every write
/// asks for a sync.
/// </summary>
public sealed class TaskList
{
    private const string Table = "tasks";
    private const string TagLinks = "task_tags";
    private const int MaxTitle = 500;
    private readonly IReplica replica;
    private readonly NewRows rows;
    private readonly AreaList areas;
    private readonly TagList tags;
    private readonly Action requestSync;

    public TaskList(IReplica replica, NewRows rows, AreaList areas, TagList tags, Action requestSync)
    {
        this.replica = replica;
        this.rows = rows;
        this.areas = areas;
        this.tags = tags;
        this.requestSync = requestSync;
        replica.Changed += (_, changed) =>
        {
            if (changed == Table)
            {
                Changed?.Invoke(this, EventArgs.Empty);
            }
        };
    }

    public event EventHandler? Changed;

    /// <summary>Open, not deleted, oldest first.</summary>
    public IReadOnlyList<TaskItem> Open() =>
        [.. replica.All(Table)
            .Where(row => row[SyncedTable.DeletedAt] is null && (string?)row["status"] == "open")
            .Select(ToItem)
            .OrderBy(item => item.CreatedAt, StringComparer.Ordinal)];

    /// <summary>Every task that isn't deleted, whatever its status: what the list rules work from.</summary>
    public IReadOnlyList<TaskItem> All() =>
        [.. replica.All(Table).Where(row => row[SyncedTable.DeletedAt] is null).Select(ToItem)];

    /// <summary>Brings a deleted task back (undo).</summary>
    public void Restore(string id) => Change(id, row => row[SyncedTable.DeletedAt] = null);

    public TaskItem? Add(string title) => Add(new ComposerDraft(title, null, null, [], null, null, false, false, null, null, []));

    /// <summary>
    /// Saves what a composer line says (docs/composer.md): the task, a new area or tags it names, and
    /// the tag links, in one transaction. Projects and ideas arrive with M5, so those parts aren't
    /// saved yet. Null when the title is blank or nobody is signed in.
    /// </summary>
    public TaskItem? Add(ComposerDraft draft)
    {
        var title = draft.Title.Trim();
        if (title.Length == 0 || rows.Owner() is null)
        {
            return null;
        }

        TaskItem? item = null;
        replica.InTransaction(() =>
        {
            var areaId = draft.Area is { } area ? areas.FindOrCreate(area)?.Id : null;
            var tagIds = draft.Tags.Select(tags.FindOrCreate).OfType<string>().Distinct(StringComparer.Ordinal).ToList();
            var task = rows.Create(Table, new Dictionary<string, JsonNode?>
            {
                ["title"] = title.Length > MaxTitle ? title[..MaxTitle] : title,
                ["notes"] = string.Empty,
                ["top_priority"] = draft.TopPriority,
                ["status"] = "open",
                ["position"] = 0.0,
                ["planned_date"] = draft.PlannedDate?.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture),
                ["planned_time"] = draft.PlannedTime?.ToString("HH:mm:ss", CultureInfo.InvariantCulture),
                ["area_id"] = areaId,
                ["recurrence"] = draft.Repeat,
            });
            if (task is null)
            {
                return;
            }

            replica.Queue(Table, task);
            foreach (var tagId in tagIds)
            {
                if (rows.Create(TagLinks, new Dictionary<string, JsonNode?> { ["task_id"] = (string?)task[SyncedTable.Id], ["tag_id"] = tagId }) is { } link)
                {
                    replica.Queue(TagLinks, link);
                }
            }

            item = ToItem(task);
        });
        if (item is not null)
        {
            requestSync();
        }

        return item;
    }

    public void SetDone(string id, bool done) => Change(id, row =>
    {
        row["status"] = done ? "done" : "open";
        row["completed_at"] = done ? rows.Timestamp() : null;
    });

    public void Delete(string id) => Change(id, row => row[SyncedTable.DeletedAt] = rows.Timestamp());

    private void Change(string id, Action<JsonObject> edit)
    {
        var row = replica.Get(Table, id);
        if (row is null)
        {
            return;
        }

        edit(row);
        replica.Queue(Table, row);
        requestSync();
    }

    private static TaskItem ToItem(JsonObject row) => new(
        (string)row[SyncedTable.Id]!,
        (string?)row["title"] ?? string.Empty,
        (string?)row["status"] switch
        {
            "done" => TaskState.Done,
            "dropped" => TaskState.Dropped,
            _ => TaskState.Open,
        },
        (bool?)row["top_priority"] ?? false,
        (string?)row[SyncedTable.CreatedAt] ?? string.Empty,
        (string?)row["planned_date"] is { } date ? DateOnly.ParseExact(date, "yyyy-MM-dd", CultureInfo.InvariantCulture) : null,
        (string?)row["planned_time"] is { } time ? TimeOnly.Parse(time, CultureInfo.InvariantCulture) : null,
        (string?)row["area_id"],
        (string?)row["recurrence"]);
}
