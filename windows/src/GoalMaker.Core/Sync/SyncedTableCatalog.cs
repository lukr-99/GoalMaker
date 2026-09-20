using System.Text.Json;

namespace GoalMaker.Core.Sync;

/// <summary>The synced tables in push and pull order, read from contracts/schemas/synced-tables.json.</summary>
public sealed class SyncedTableCatalog
{
    private readonly Dictionary<string, SyncedTable> byName;

    public SyncedTableCatalog(IReadOnlyList<SyncedTable> tables)
    {
        Tables = tables;
        byName = tables.ToDictionary(table => table.Name, StringComparer.Ordinal);
    }

    /// <summary>Parents before children, the order pushes and pulls follow.</summary>
    public IReadOnlyList<SyncedTable> Tables { get; }

    public SyncedTable this[string name] =>
        byName.TryGetValue(name, out var table) ? table : throw new KeyNotFoundException($"'{name}' is not a synced table");

    public static SyncedTableCatalog Load(Stream json)
    {
        using var document = JsonDocument.Parse(json);
        var tables = document.RootElement.GetProperty("tables").EnumerateArray()
            .Select(table => new SyncedTable(
                table.GetProperty("name").GetString()!,
                [.. table.GetProperty("columns").EnumerateArray().Select(column => new SyncedColumn(
                    column.GetProperty("name").GetString()!,
                    Enum.Parse<ColumnKind>(column.GetProperty("kind").GetString()!, ignoreCase: true),
                    column.TryGetProperty("required", out var required) && required.GetBoolean()))]))
            .ToList();
        return new SyncedTableCatalog(tables);
    }
}
