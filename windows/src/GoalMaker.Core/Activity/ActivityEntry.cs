using System.Text.Json.Nodes;

namespace GoalMaker.Core.Activity;

/// <summary>
/// One row of the server's activity log (supabase/migrations/0004 and 0007): who changed which row,
/// how, the row before and after, and when it was undone, if it was.
/// </summary>
public sealed record ActivityEntry(
    long Id,
    string Entity,
    string EntityId,
    string Action,
    string Actor,
    JsonObject? Before,
    JsonObject After,
    DateTimeOffset CreatedAt,
    DateTimeOffset? UndoneAt)
{
    public ActivityChange Change => ActivityRules.Change(Entity, Action, Before, After);
}
