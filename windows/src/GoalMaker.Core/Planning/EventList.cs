using System.Globalization;
using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Planning;

/// <summary>
/// The owner's calendar events, read from the replica and changed through its outbox
/// (docs/calendar.md, ADR 0019). Every write asks for a sync.
/// </summary>
public sealed class EventList
{
    private const string Table = "events";
    private readonly IReplica replica;
    private readonly NewRows rows;
    private readonly Action requestSync;

    public EventList(IReplica replica, NewRows rows, Action requestSync)
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

    /// <summary>Raised after every change to an event.</summary>
    public event EventHandler? Changed;

    /// <summary>Every event that isn't deleted, in day order (<see cref="EventRules.Order"/>).</summary>
    public IReadOnlyList<EventItem> All() => EventRules.Order(replica.All(Table).Select(ToItem));

    /// <summary>The events that touch a day from <paramref name="from"/> to <paramref name="to"/>, in day order.</summary>
    public IReadOnlyList<EventItem> Between(DateOnly from, DateOnly to) =>
        [.. All().Where(item => item.StartsOn <= to && item.EndsOn >= from)];

    public EventItem? Get(string id) =>
        replica.Get(Table, id) is { } row && ToItem(row) is { Deleted: false } item ? item : null;

    /// <summary>Adds an event. Null when the server would refuse it (<see cref="EventRules.Check"/>) or nobody is signed in.</summary>
    public EventItem? Add(EventDraft draft)
    {
        if (EventRules.Check(draft) is not { } clean)
        {
            return null;
        }

        var values = Values(clean);
        values["made_by"] = ProjectRules.Owner;
        if (rows.Create(Table, values) is not { } row)
        {
            return null;
        }

        Queue(row);
        return ToItem(row);
    }

    /// <summary>Changes an event to <paramref name="draft"/>; false when it is gone or the draft is not valid.</summary>
    public bool Update(string id, EventDraft draft)
    {
        if (EventRules.Check(draft) is not { } clean || replica.Get(Table, id) is not { } row || row[SyncedTable.DeletedAt] is not null)
        {
            return false;
        }

        foreach (var (column, value) in Values(clean))
        {
            row[column] = value;
        }

        Queue(row);
        return true;
    }

    public bool Delete(string id)
    {
        if (replica.Get(Table, id) is not { } row || row[SyncedTable.DeletedAt] is not null)
        {
            return false;
        }

        row[SyncedTable.DeletedAt] = rows.Timestamp();
        Queue(row);
        return true;
    }

    /// <summary>Brings a deleted event back, as the undo after a delete does.</summary>
    public bool Restore(string id)
    {
        if (replica.Get(Table, id) is not { } row || row[SyncedTable.DeletedAt] is null)
        {
            return false;
        }

        row[SyncedTable.DeletedAt] = null;
        Queue(row);
        return true;
    }

    private static Dictionary<string, JsonNode?> Values(EventDraft draft) => new()
    {
        ["title"] = draft.Title,
        ["starts_on"] = Format(draft.StartsOn),
        ["ends_on"] = Format(draft.EndsOn),
        ["notes"] = draft.Notes,
        ["area_id"] = draft.AreaId,
    };

    private static EventItem ToItem(JsonObject row)
    {
        var starts = Parse((string?)row["starts_on"]) ?? DateOnly.MinValue;
        var ends = Parse((string?)row["ends_on"]) ?? starts;
        return new EventItem(
            (string?)row[SyncedTable.Id] ?? string.Empty,
            (string?)row["title"] ?? string.Empty,
            starts,
            ends < starts ? starts : ends,
            Notes: (string?)row["notes"],
            AreaId: (string?)row["area_id"],
            MadeBy: (string?)row["made_by"] ?? ProjectRules.Owner,
            CreatedAt: (string?)row[SyncedTable.CreatedAt] ?? string.Empty,
            Deleted: row[SyncedTable.DeletedAt] is not null);
    }

    private static string Format(DateOnly day) => day.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);

    private static DateOnly? Parse(string? text) =>
        text is not null && DateOnly.TryParseExact(text.Length > 10 ? text[..10] : text, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out var day)
            ? day
            : null;

    private void Queue(JsonObject row)
    {
        replica.Queue(Table, row);
        requestSync();
    }
}
