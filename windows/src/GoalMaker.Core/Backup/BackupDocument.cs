using System.Text.Json.Nodes;

namespace GoalMaker.Core.Backup;

/// <summary>
/// One export: who it belongs to, what wrote it, and every row it carries, table by table
/// (docs/backup.md). Tombstones are left out, so a row may point at something the file does not
/// carry.
/// </summary>
public sealed record BackupDocument(
    int Version,
    string ExportedAt,
    string App,
    string AppVersion,
    string Owner,
    IReadOnlyDictionary<string, IReadOnlyList<JsonObject>> Tables)
{
    public int RowCount => Tables.Values.Sum(rows => rows.Count);
}
