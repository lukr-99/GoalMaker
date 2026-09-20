namespace GoalMaker.Core.Sync;

/// <summary>A table both apps replicate, with its columns in contract order.</summary>
public sealed record SyncedTable(string Name, IReadOnlyList<SyncedColumn> Columns)
{
    /// <summary>The columns the server needs a value in, so a new row has to carry all of them.</summary>
    public IReadOnlyList<string> Required { get; } =
        [.. Columns.Where(column => column.Required).Select(column => column.Name)];

    public const string Id = "id";
    public const string OwnerId = "owner_id";
    public const string CreatedAt = "created_at";
    public const string UpdatedAt = "updated_at";
    public const string DeletedAt = "deleted_at";
}
