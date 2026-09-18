using System.Text.Json.Nodes;

namespace GoalMaker.Core.Sync;

/// <summary>
/// The server's copy of the synced tables, through PostgREST with the user's session. Throws
/// <see cref="RemoteUnavailableException"/> for temporary failures and
/// <see cref="RemoteRejectedException"/> for rows the server refuses.
/// </summary>
public interface IRemoteTables
{
    /// <summary>Upserts the row by id and returns the stored row.</summary>
    Task<JsonObject> UpsertAsync(string table, JsonObject row, CancellationToken cancellationToken);

    /// <summary>
    /// Rows with updated_at at or after <paramref name="from"/> (all rows when null), tombstones
    /// included, ordered by (updated_at, id), starting after <paramref name="after"/>.
    /// </summary>
    Task<IReadOnlyList<JsonObject>> PullAsync(
        string table,
        string? from,
        RowCursor? after,
        int limit,
        CancellationToken cancellationToken);
}
