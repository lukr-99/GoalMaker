using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Planning;

/// <summary>
/// Reminders as the device schedules them: read from the replica, written through its outbox
/// (docs/reminders.md). The table keeps instants, so this is where they become the device's local
/// times and back.
/// </summary>
public sealed class ReminderList
{
    private const string Table = "reminders";
    private readonly IReplica replica;
    private readonly NewRows rows;
    private readonly Action requestSync;
    private readonly Func<TimeZoneInfo> zone;

    public ReminderList(IReplica replica, NewRows rows, Action requestSync, Func<TimeZoneInfo>? zone = null)
    {
        this.replica = replica;
        this.rows = rows;
        this.requestSync = requestSync;
        this.zone = zone ?? (() => TimeZoneInfo.Local);
        replica.Changed += (_, changed) =>
        {
            if (changed == Table)
            {
                Changed?.Invoke(this, EventArgs.Empty);
            }
        };
    }

    /// <summary>Raised after every change to the reminders table.</summary>
    public event EventHandler? Changed;

    /// <summary>Every reminder that isn't deleted.</summary>
    public IReadOnlyList<ReminderItem> All() =>
        [.. replica.All(Table).Where(row => row[SyncedTable.DeletedAt] is null).Select(ToItem)];

    /// <summary>The reminders on one task, soonest first.</summary>
    public IReadOnlyList<ReminderItem> ForTask(string taskId) =>
        [.. All()
            .Where(reminder => reminder.TaskId == taskId)
            .OrderBy(reminder => reminder.FireAt)
            .ThenBy(reminder => reminder.OffsetMinutes)
            .ThenBy(reminder => reminder.Id, StringComparer.Ordinal)];

    /// <summary>A reminder at its own time. Null when nobody is signed in.</summary>
    public ReminderItem? AddAt(string taskId, DateTime at, bool important = false) =>
        Add(taskId, important, Text(at), null);

    /// <summary>A reminder <paramref name="minutes"/> before the task's planned time. Null when nobody is signed in.</summary>
    public ReminderItem? AddBefore(string taskId, int minutes, bool important = false) =>
        Add(taskId, important, null, -minutes);

    /// <summary>Handled from a notification: it never fires again, and the other device drops its copy.</summary>
    public void MarkDone(string id) => Settle(id, "done");

    /// <summary>Dismissed: it never fires again.</summary>
    public void Dismiss(string id) => Settle(id, "dismissed");

    /// <summary>Comes back at <paramref name="until"/> instead of its own time.</summary>
    public void Snooze(string id, DateTime until) => Change(id, row =>
    {
        row["state"] = "snoozed";
        row["snoozed_until"] = Text(until);
    });

    public void Delete(string id) => Change(id, row => row[SyncedTable.DeletedAt] = rows.Timestamp());

    private ReminderItem? Add(string taskId, bool important, string? fireAt, int? offsetMinutes)
    {
        if (rows.Owner() is null)
        {
            return null;
        }

        var row = rows.Create(Table, new Dictionary<string, JsonNode?>
        {
            ["task_id"] = taskId,
            ["important"] = important,
            ["state"] = "pending",
            ["snoozed_until"] = null,
            ["fire_at"] = fireAt,
            ["offset_minutes"] = offsetMinutes,
        });
        if (row is null)
        {
            return null;
        }

        replica.Queue(Table, row);
        requestSync();
        return ToItem(row);
    }

    private void Settle(string id, string state) => Change(id, row =>
    {
        row["state"] = state;
        row["snoozed_until"] = null;
    });

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

    private string Text(DateTime local) =>
        SyncRules.Format(new DateTimeOffset(DateTime.SpecifyKind(local, DateTimeKind.Unspecified), zone().GetUtcOffset(local)));

    // A row written here holds an int; one read back from SQLite or the server holds a long or a
    // double. All of them are whole minutes.
    private static int? Minutes(JsonNode? value) => value switch
    {
        JsonValue number when number.TryGetValue<int>(out var small) => small,
        JsonValue number when number.TryGetValue<long>(out var large) => (int)large,
        JsonValue number when number.TryGetValue<double>(out var real) => (int)Math.Round(real),
        _ => null,
    };

    private DateTime? Local(JsonNode? value) =>
        (string?)value is { } text && SyncRules.InstantOf(text) is { } instant
            ? TimeZoneInfo.ConvertTime(instant, zone()).DateTime
            : null;

    private ReminderItem ToItem(JsonObject row) => new(
        (string?)row[SyncedTable.Id] ?? string.Empty,
        (string?)row["task_id"] ?? string.Empty,
        (string?)row["state"] switch
        {
            "snoozed" => ReminderState.Snoozed,
            "dismissed" => ReminderState.Dismissed,
            "done" => ReminderState.Done,
            _ => ReminderState.Pending,
        },
        Important: row["important"] is JsonValue important && important.TryGetValue<bool>(out var flag) && flag,
        FireAt: Local(row["fire_at"]),
        OffsetMinutes: Minutes(row["offset_minutes"]),
        SnoozedUntil: Local(row["snoozed_until"]));
}
