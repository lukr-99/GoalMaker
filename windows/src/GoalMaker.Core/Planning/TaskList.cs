using System.Globalization;
using System.Text.Json.Nodes;
using GoalMaker.Core.Composer;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Planning;

/// <summary>
/// Tasks as the lists need them: read from the replica, written through its outbox. Every write
/// asks for a sync. Finishing a repeating task makes its next occurrence (docs/repeating.md);
/// <paramref name="today"/> is the planning day that counts from.
/// </summary>
public sealed class TaskList
{
    private const string Table = "tasks";
    private const string TagLinks = "task_tags";
    private const string SeriesId = "series_id";
    private const int MaxTitle = 500;
    private const int MaxNotes = 20_000;
    private readonly IReplica replica;
    private readonly NewRows rows;
    private readonly AreaList areas;
    private readonly TagList tags;
    private readonly Action requestSync;
    private readonly Func<DateOnly> today;

    public TaskList(IReplica replica, NewRows rows, AreaList areas, TagList tags, Action requestSync, Func<DateOnly> today)
    {
        this.today = today;
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

            if (draft.Repeat is not null)
            {
                task[SeriesId] = (string?)task[SyncedTable.Id];
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

    public void SetDone(string id, bool done)
    {
        if (done)
        {
            Finish(id, "done");
        }
        else
        {
            Reopen(id, _ => { });
        }
    }

    public void Delete(string id) => Change(id, row => row[SyncedTable.DeletedAt] = rows.Timestamp());

    /// <summary>Plans the task for <paramref name="day"/>, keeping its time; reopens it if it was done or dropped (Plan tomorrow).</summary>
    public void Plan(string id, DateOnly day) =>
        Reopen(id, row => row["planned_date"] = day.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture));

    /// <summary>Drops the task: it stays in the history but leaves every list. A repeating task moves on.</summary>
    public void Drop(string id) => Finish(id, "dropped");

    /// <summary>
    /// After a sync that pulled rows: drops all but one open occurrence of each series, the same way
    /// on every device (docs/repeating.md). True when it changed something.
    /// </summary>
    public bool RepairSeries()
    {
        var drop = Occurrences.ToDrop(All());
        if (drop.Count == 0)
        {
            return false;
        }

        replica.InTransaction(() =>
        {
            foreach (var id in drop)
            {
                if (replica.Get(Table, id) is { } row)
                {
                    row["status"] = "dropped";
                    row["completed_at"] = null;
                    replica.Queue(Table, row);
                }
            }
        });
        requestSync();
        return true;
    }

    public void SetTopPriority(string id, bool top) => Change(id, row => row["top_priority"] = top);

    /// <summary>The task with this id, whatever its status, or null when it is gone.</summary>
    public TaskItem? Find(string id) => All().FirstOrDefault(task => task.Id == id);

    /// <summary>A new title. False when it is blank, which the task can't have.</summary>
    public bool Rename(string id, string title)
    {
        var trimmed = title.Trim();
        trimmed = trimmed.Length > MaxTitle ? trimmed[..MaxTitle] : trimmed;
        if (trimmed.Length == 0)
        {
            return false;
        }

        Change(id, row => row["title"] = trimmed);
        return true;
    }

    /// <summary>The notes, in light Markdown (docs/archive.md), up to 20,000 characters.</summary>
    public void SetNotes(string id, string notes) => Change(id, row => row["notes"] = notes.Length > MaxNotes ? notes[..MaxNotes] : notes);

    /// <summary>
    /// The planned day and time, without reopening the task (Plan tomorrow's <see cref="Plan"/> does
    /// that). A time needs a day, so no day clears the time too.
    /// </summary>
    public void Schedule(string id, DateOnly? day, TimeOnly? time) => Change(id, row =>
    {
        row["planned_date"] = day?.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
        row["planned_time"] = day is null ? null : time?.ToString("HH:mm:ss", CultureInfo.InvariantCulture);
    });

    public void SetDeadline(string id, DateOnly? day) => Change(id, row => row["deadline"] = day?.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture));

    public void SetArea(string id, string? areaId) => Change(id, row => row["area_id"] = areaId);

    /// <summary>
    /// How the task repeats (docs/repeating.md), or not at all when <paramref name="rule"/> is null.
    /// False for a rule the apps can't follow. A task that starts repeating becomes the first of its series.
    /// </summary>
    public bool SetRecurrence(string id, string? rule)
    {
        if (rule is not null && Planning.Recurrence.Parse(rule) is null)
        {
            return false;
        }

        Change(id, row =>
        {
            row["recurrence"] = rule;
            if (rule is not null && row[SeriesId] is null)
            {
                row[SeriesId] = id;
            }
        });
        return true;
    }

    /// <summary>Links the task to exactly these tags, by name, creating tags it names for the first time.</summary>
    public void SetTags(string id, IEnumerable<string> names)
    {
        replica.InTransaction(() =>
        {
            var wanted = names.Select(tags.FindOrCreate).OfType<string>().ToHashSet(StringComparer.Ordinal);
            var links = replica.All(TagLinks).Where(link => (string?)link["task_id"] == id && link[SyncedTable.DeletedAt] is null).ToList();
            foreach (var link in links.Where(link => !wanted.Contains((string?)link["tag_id"] ?? string.Empty)))
            {
                link[SyncedTable.DeletedAt] = rows.Timestamp();
                replica.Queue(TagLinks, link);
            }

            var linked = links.Select(link => (string?)link["tag_id"]).OfType<string>().ToHashSet(StringComparer.Ordinal);
            foreach (var tagId in wanted.Where(tagId => !linked.Contains(tagId)))
            {
                if (rows.Create(TagLinks, new Dictionary<string, JsonNode?> { ["task_id"] = id, ["tag_id"] = tagId }) is { } link)
                {
                    replica.Queue(TagLinks, link);
                }
            }
        });
        requestSync();
    }

    // Done or dropped; an open repeating task makes its next occurrence in the same transaction.
    private void Finish(string id, string status)
    {
        var changed = false;
        replica.InTransaction(() =>
        {
            if (replica.Get(Table, id) is not { } row)
            {
                return;
            }

            var wasOpen = (string?)row["status"] == "open";
            row["status"] = status;
            row["completed_at"] = status == "done" ? rows.Timestamp() : null;
            replica.Queue(Table, row);
            if (wasOpen)
            {
                MoveOn(row);
            }

            changed = true;
        });
        if (changed)
        {
            requestSync();
        }
    }

    // Open again; a finished occurrence takes back its next one if that is still open (docs/repeating.md).
    private void Reopen(string id, Action<JsonObject> edit)
    {
        var changed = false;
        replica.InTransaction(() =>
        {
            if (replica.Get(Table, id) is not { } row)
            {
                return;
            }

            var wasFinished = (string?)row["status"] != "open";
            row["status"] = "open";
            row["completed_at"] = null;
            edit(row);
            replica.Queue(Table, row);
            if (wasFinished && replica.Get(Table, Occurrences.SuccessorId(id)) is { } next
                && next[SyncedTable.DeletedAt] is null && (string?)next["status"] == "open")
            {
                next[SyncedTable.DeletedAt] = rows.Timestamp();
                replica.Queue(Table, next);
            }

            changed = true;
        });
        if (changed)
        {
            requestSync();
        }
    }

    // The next occurrence copies this one's plan onto the rule's next day, with its tags; nothing when
    // the task doesn't repeat, the rule can't be followed, or the next occurrence is already there.
    private void MoveOn(JsonObject row)
    {
        var current = ToItem(row);
        if (Recurrence.Parse(current.Recurrence)?.Next(current.PlannedDate, today()) is not { } day)
        {
            return;
        }

        var nextId = Occurrences.SuccessorId(current.Id);
        if (replica.Get(Table, nextId) is { } existing && existing[SyncedTable.DeletedAt] is null)
        {
            return;
        }

        var next = rows.Create(Table, new Dictionary<string, JsonNode?>
        {
            [SyncedTable.Id] = nextId,
            ["title"] = row["title"]?.DeepClone(),
            ["notes"] = row["notes"]?.DeepClone() ?? string.Empty,
            ["top_priority"] = current.TopPriority,
            ["status"] = "open",
            ["position"] = 0.0,
            ["planned_date"] = day.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture),
            ["planned_time"] = row["planned_time"]?.DeepClone(),
            ["area_id"] = current.AreaId,
            ["recurrence"] = current.Recurrence,
            [SeriesId] = Occurrences.SeriesOf(current),
        });
        if (next is null)
        {
            return;
        }

        replica.Queue(Table, next);
        foreach (var link in replica.All(TagLinks))
        {
            if ((string?)link["task_id"] == current.Id && link[SyncedTable.DeletedAt] is null && (string?)link["tag_id"] is { } tagId
                && rows.Create(TagLinks, new Dictionary<string, JsonNode?>
                {
                    [SyncedTable.Id] = Occurrences.TagLinkId(nextId, tagId),
                    ["task_id"] = nextId,
                    ["tag_id"] = tagId,
                }) is { } copy)
            {
                replica.Queue(TagLinks, copy);
            }
        }
    }

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
        (string?)row["recurrence"],
        SeriesId: (string?)row[SeriesId],
        Notes: (string?)row["notes"] ?? string.Empty,
        Deadline: (string?)row["deadline"] is { } deadline ? DateOnly.ParseExact(deadline, "yyyy-MM-dd", CultureInfo.InvariantCulture) : null,
        CompletedAt: (string?)row["completed_at"]);
}
