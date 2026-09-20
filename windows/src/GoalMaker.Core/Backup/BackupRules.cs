using System.Globalization;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Backup;

/// <summary>
/// The export both apps write and read (docs/backup.md), pinned by contracts/vectors/backup.json:
/// what the file says, what a reader checks before it changes anything, and which row wins when the
/// file and the replica both have one. Pure: it never touches a file or a replica.
/// </summary>
public static class BackupRules
{
    /// <summary>What every export says it is.</summary>
    public const string Format = "goalmaker.backup";

    /// <summary>The version this build writes, and the newest it can read.</summary>
    public const int Version = 1;

    /// <summary>The name a file is offered under, with the day it was written.</summary>
    public static string FileName(string day) => $"goalmaker-{day}.json";

    /// <summary>
    /// The file as text: keys in the order below, tables in <paramref name="order"/>, two spaces of
    /// indent and a newline at the end, so an export from either app is the same bytes.
    /// </summary>
    public static string Write(BackupDocument document, IReadOnlyList<string> order)
    {
        var text = new StringBuilder("{\n");
        Line(text, 1, "format", JsonValue.Create(Format), last: false);
        Line(text, 1, "version", JsonValue.Create(document.Version), last: false);
        Line(text, 1, "exportedAt", JsonValue.Create(document.ExportedAt), last: false);
        Line(text, 1, "app", JsonValue.Create(document.App), last: false);
        Line(text, 1, "appVersion", JsonValue.Create(document.AppVersion), last: false);
        Line(text, 1, "owner", JsonValue.Create(document.Owner), last: false);

        var tables = order.Where(document.Tables.ContainsKey).ToList();
        text.Append("  \"tables\": {");
        text.Append(tables.Count == 0 ? "}\n" : "\n");
        for (var index = 0; index < tables.Count; index++)
        {
            var rows = document.Tables[tables[index]];
            text.Append("    ").Append(Quote(tables[index])).Append(": [");
            text.Append(rows.Count == 0 ? "]" : "\n");
            for (var row = 0; row < rows.Count; row++)
            {
                text.Append("      {\n");
                var columns = rows[row].ToList();
                for (var column = 0; column < columns.Count; column++)
                {
                    Line(text, 4, columns[column].Key, columns[column].Value, last: column == columns.Count - 1);
                }

                text.Append("      }").Append(row == rows.Count - 1 ? "\n" : ",\n");
            }

            if (rows.Count > 0)
            {
                text.Append("    ]");
            }

            text.Append(index == tables.Count - 1 ? "\n" : ",\n");
        }

        if (tables.Count > 0)
        {
            text.Append("  }\n");
        }

        return text.Append("}\n").ToString();
    }

    /// <summary>The document a file holds, or null when it is not JSON with the parts an export has.</summary>
    public static BackupDocument? Read(JsonNode? node)
    {
        if (node is not JsonObject root)
        {
            return null;
        }

        var tables = new Dictionary<string, IReadOnlyList<JsonObject>>(StringComparer.Ordinal);
        if (root["tables"] is JsonObject carried)
        {
            foreach (var (table, rows) in carried)
            {
                tables[table] = rows is JsonArray array
                    ? [.. array.OfType<JsonObject>()]
                    : [];
            }
        }

        if (Text(root["format"]) is null && root["tables"] is null)
        {
            return null;
        }

        return new BackupDocument(
            Version: root["version"]?.GetValue<int>() ?? 0,
            ExportedAt: Text(root["exportedAt"]) ?? string.Empty,
            App: Text(root["app"]) ?? string.Empty,
            AppVersion: Text(root["appVersion"]) ?? string.Empty,
            Owner: Text(root["owner"]) ?? string.Empty,
            Tables: tables);
    }

    /// <summary>
    /// What is wrong with this file for this owner, or null when it can be restored. The whole file
    /// is judged before anything is written, so a refusal leaves the replica untouched.
    /// </summary>
    public static BackupProblem? Check(JsonObject root, string owner, IReadOnlySet<string> known)
    {
        if (Text(root["format"]) != Format)
        {
            return BackupProblem.NotABackup;
        }

        if (root["version"] is not JsonValue version || !version.TryGetValue<int>(out var number))
        {
            return BackupProblem.NotABackup;
        }

        if (number > Version)
        {
            return BackupProblem.TooNew;
        }

        if (Text(root["owner"]) != owner)
        {
            return BackupProblem.AnotherOwner;
        }

        if (root["tables"] is not JsonObject tables)
        {
            return BackupProblem.NotABackup;
        }

        foreach (var (table, rows) in tables)
        {
            if (!known.Contains(table))
            {
                return BackupProblem.UnknownTable;
            }

            foreach (var row in rows as JsonArray ?? [])
            {
                if (row is not JsonObject values || string.IsNullOrWhiteSpace(Text(values[SyncedTable.Id])))
                {
                    return BackupProblem.RowWithoutId;
                }

                if (Text(values[SyncedTable.OwnerId]) is { } rowOwner && rowOwner != owner)
                {
                    return BackupProblem.AnotherOwner;
                }
            }
        }

        return null;
    }

    /// <summary>
    /// Whether a restore takes the file's row over the one already here, by the server timestamp
    /// both carry: the rule sync uses, so a restore never undoes newer work. A row that is not here
    /// is always taken.
    /// </summary>
    public static bool TakesFile(string? local, string? file)
    {
        if (local is null)
        {
            return true;
        }

        return file is not null && string.CompareOrdinal(file, local) > 0;
    }

    private static string? Text(JsonNode? node) =>
        node is JsonValue value && value.TryGetValue<string>(out var text) ? text : null;

    private static void Line(StringBuilder text, int depth, string name, JsonNode? value, bool last)
    {
        text.Append(new string(' ', depth * 2)).Append(Quote(name)).Append(": ").Append(Value(value));
        text.Append(last ? "\n" : ",\n");
    }

    private static string Value(JsonNode? node) => node switch
    {
        null => "null",
        JsonValue value when value.TryGetValue<string>(out var text) => Quote(text),
        JsonValue value when value.TryGetValue<bool>(out var flag) => flag ? "true" : "false",
        JsonValue value => value.ToJsonString(),
        _ => node.ToJsonString(),
    };

    // JSON's own escapes only: text outside ASCII is written as itself, so both apps write the same bytes.
    private static string Quote(string text)
    {
        var quoted = new StringBuilder("\"");
        foreach (var character in text)
        {
            switch (character)
            {
                case '"': quoted.Append("\\\""); break;
                case '\\': quoted.Append("\\\\"); break;
                case '\n': quoted.Append("\\n"); break;
                case '\r': quoted.Append("\\r"); break;
                case '\t': quoted.Append("\\t"); break;
                case '\b': quoted.Append("\\b"); break;
                case '\f': quoted.Append("\\f"); break;
                default:
                    quoted.Append(character < ' '
                        ? "\\u" + ((int)character).ToString("x4", CultureInfo.InvariantCulture)
                        : character);
                    break;
            }
        }

        return quoted.Append('"').ToString();
    }
}
