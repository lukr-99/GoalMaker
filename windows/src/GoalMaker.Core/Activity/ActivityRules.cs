using System.Text.Json;
using System.Text.Json.Nodes;

namespace GoalMaker.Core.Activity;

/// <summary>What an activity log entry did, in the words the Activity page uses (contracts/vectors/activity.json).</summary>
public static class ActivityRules
{
    public static ActivityChange Change(string entity, string action, JsonObject? before, JsonObject after)
    {
        var subject = entity switch
        {
            "tasks" or "task_steps" => Text(after, "title"),
            "areas" or "tags" => Text(after, "name"),
            _ => null,
        };
        var change = action switch
        {
            "create" => "added",
            "delete" => "deleted",
            "restore" => "restored",
            _ => Updated(entity, before, after),
        };
        return new ActivityChange(change, subject, change == "moved" ? Text(after, "planned_date") : null);
    }

    private static string Updated(string entity, JsonObject? before, JsonObject after)
    {
        bool Changed(string column) => Text(before, column) != Text(after, column);
        var status = Text(after, "status");
        return entity switch
        {
            "tasks" when Changed("status") && status == "done" => "completed",
            "tasks" when Changed("status") && status == "dropped" => "dropped",
            "tasks" when Changed("status") && status == "open" => "reopened",
            "tasks" when Changed("planned_date") => "moved",
            "tasks" when Changed("title") => "renamed",
            "areas" when Changed("archived_at") => Text(after, "archived_at") is not null ? "archived" : "unarchived",
            "areas" or "tags" when Changed("name") => "renamed",
            "task_steps" when Changed("done") => Text(after, "done") == "true" ? "checked" : "unchecked",
            "task_steps" when Changed("title") => "renamed",
            "reminders" when Changed("state") && Text(after, "state") is "done" or "dismissed" => "handled",
            "reminders" when Changed("state") && Text(after, "state") == "snoozed" => "snoozed",
            _ => "edited",
        };
    }

    // A column's value as text; missing and JSON null are both null, and booleans read true or false.
    private static string? Text(JsonObject? row, string column) => row?[column] switch
    {
        null => null,
        JsonValue value when value.GetValueKind() == JsonValueKind.True => "true",
        JsonValue value when value.GetValueKind() == JsonValueKind.False => "false",
        JsonValue value when value.GetValueKind() == JsonValueKind.String => value.GetValue<string>(),
        var node => node.ToJsonString(),
    };
}
