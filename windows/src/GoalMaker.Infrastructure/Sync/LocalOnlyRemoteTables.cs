using System.Globalization;
using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;

namespace GoalMaker.Infrastructure.Sync;

/// <summary>
/// A dev build's server: it keeps nothing and has nothing new, so every change counts as pushed and the
/// replica is the only copy (docs/sync.md). It stamps updated_at as the real server would. The engine
/// that uses it never pulls (<see cref="SyncEngine"/>), since an empty answer reads as a full resync.
/// </summary>
public sealed class LocalOnlyRemoteTables(TimeProvider time) : IRemoteTables
{
    public Task<JsonObject> UpsertAsync(string table, JsonObject row, CancellationToken cancellationToken)
    {
        var stored = (JsonObject)row.DeepClone();
        stored[SyncedTable.UpdatedAt] = time.GetUtcNow().ToString("O", CultureInfo.InvariantCulture);
        return Task.FromResult(stored);
    }

    public Task<IReadOnlyList<JsonObject>> PullAsync(
        string table,
        string? from,
        RowCursor? after,
        int limit,
        CancellationToken cancellationToken) =>
        Task.FromResult<IReadOnlyList<JsonObject>>([]);
}
