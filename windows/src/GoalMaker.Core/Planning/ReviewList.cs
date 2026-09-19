using System.Globalization;
using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Planning;

/// <summary>
/// The owner's reviews, read from the replica and changed through its outbox (docs/reviews.md). A
/// review's id comes from the owner, the kind and the period, so the same review written here and on
/// the phone is one row. Every write asks for a sync.
/// </summary>
public sealed class ReviewList
{
    private const string Table = "reviews";
    private const int MaxReflections = 20;
    private const int MaxPromptId = 60;
    private const int MaxAnswer = 4000;
    private const int MaxSummary = 20000;
    private readonly IReplica replica;
    private readonly NewRows rows;
    private readonly Action requestSync;

    public ReviewList(IReplica replica, NewRows rows, Action requestSync)
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

    /// <summary>Raised after every change to the reviews.</summary>
    public event EventHandler? Changed;

    /// <summary>Every review that isn't deleted, newest period first.</summary>
    public IReadOnlyList<ReviewItem> All() =>
        [.. replica.All(Table).Select(ToItem).Where(review => !review.Deleted)
            .OrderByDescending(review => review.PeriodStart)
            .ThenBy(review => review.Kind, StringComparer.Ordinal)];

    public ReviewItem? Find(string kind, DateOnly periodStart)
    {
        if (rows.Owner() is not { } owner)
        {
            return null;
        }

        return replica.Get(Table, ReviewRules.IdOf(owner, kind, periodStart)) is { } row && ToItem(row) is { Deleted: false } review
            ? review
            : null;
    }

    /// <summary>The review of that period, made when it is the first time anyone opens it.</summary>
    public ReviewItem? Open(string kind, DateOnly periodStart)
    {
        if (Find(kind, periodStart) is { } existing)
        {
            return existing;
        }

        if (rows.Owner() is not { } owner)
        {
            return null;
        }

        var row = rows.Create(Table, new Dictionary<string, JsonNode?>
        {
            [SyncedTable.Id] = ReviewRules.IdOf(owner, kind, periodStart),
            ["kind"] = kind,
            ["period_start"] = periodStart.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture),
            ["summary"] = string.Empty,
            ["reflections"] = new JsonArray(),
        });
        if (row is null)
        {
            return null;
        }

        replica.Queue(Table, row);
        requestSync();
        return ToItem(row);
    }

    /// <summary>How the period felt, from 1 to 5; null clears it.</summary>
    public bool SetMood(string id, int? mood) => Change(id, row => row["mood"] = Rating(mood));

    public bool SetEnergy(string id, int? energy) => Change(id, row => row["energy"] = Rating(energy));

    /// <summary>The answers written, in the order they were asked; blank answers keep the prompt in place.</summary>
    public bool SetReflections(string id, IEnumerable<Reflection> reflections) => Change(id, row =>
    {
        var array = new JsonArray();
        foreach (var reflection in reflections.Take(MaxReflections))
        {
            array.Add(new JsonObject
            {
                ["prompt"] = Clip(reflection.PromptId, MaxPromptId),
                ["answer"] = Clip(reflection.Answer, MaxAnswer),
            });
        }

        row["reflections"] = array;
    });

    /// <summary>The summary a Claude routine wrote, or the owner's own closing words.</summary>
    public bool SetSummary(string id, string summary) => Change(id, row => row["summary"] = Clip(summary, MaxSummary));

    public bool Delete(string id) => Change(id, row => row[SyncedTable.DeletedAt] = rows.Timestamp());

    private static string Clip(string text, int length) => text.Length > length ? text[..length] : text;

    private static JsonNode? Rating(int? value) => value is >= 1 and <= 5 ? value : null;

    private static ReviewItem ToItem(JsonObject row) => new(
        (string?)row[SyncedTable.Id] ?? string.Empty,
        (string?)row["kind"] ?? ReviewRules.Weekly,
        (string?)row["period_start"] is { } start ? DateOnly.ParseExact(start, "yyyy-MM-dd", CultureInfo.InvariantCulture) : DateOnly.MinValue)
    {
        Mood = Rank(row["mood"]),
        Energy = Rank(row["energy"]),
        Summary = (string?)row["summary"] ?? string.Empty,
        Reflections = [.. (row["reflections"] as JsonArray ?? []).OfType<JsonObject>()
            .Where(reflection => (string?)reflection["prompt"] is not null)
            .Select(reflection => new Reflection((string)reflection["prompt"]!, (string?)reflection["answer"] ?? string.Empty))],
        Deleted = row[SyncedTable.DeletedAt] is not null,
    };

    private static int? Rank(JsonNode? node) => node is JsonValue value
        ? value.TryGetValue<int>(out var rank) ? rank : value.TryGetValue<long>(out var wide) ? (int)wide : null
        : null;

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
}
