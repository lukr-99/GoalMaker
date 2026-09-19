using System.Security.Cryptography;
using System.Text;

namespace GoalMaker.Core.Planning;

/// <summary>
/// How a repeating task's series moves on across devices (docs/repeating.md,
/// contracts/vectors/recurrence.json): the ids the next occurrence and its tag links get, and which
/// open occurrences to drop when a sync left a series with more than one.
/// </summary>
public static class Occurrences
{
    private const string Namespace = "77797aa7-9e11-42d8-a667-24b0596d2a4f";

    /// <summary>The next occurrence's id: the same on every device that moves this occurrence on.</summary>
    public static string SuccessorId(string id) => NameBased(id.ToLowerInvariant());

    /// <summary>The id of a next occurrence's link to a tag.</summary>
    public static string TagLinkId(string taskId, string tagId) => NameBased($"{taskId.ToLowerInvariant()}/{tagId.ToLowerInvariant()}");

    /// <summary>The series a task belongs to: its series_id, or its own id when it has none.</summary>
    public static string SeriesOf(TaskItem task) => task.SeriesId ?? task.Id;

    /// <summary>The open occurrences to drop so each series keeps one: the one planned latest (ties: the larger id).</summary>
    public static IReadOnlyList<string> ToDrop(IEnumerable<TaskItem> tasks) =>
        [.. tasks.Where(task => !task.Deleted && task.State == TaskState.Open)
            .GroupBy(SeriesOf, StringComparer.Ordinal)
            .Where(series => series.Count() > 1)
            .SelectMany(series => series
                .OrderByDescending(task => task.PlannedDate ?? DateOnly.MinValue)
                .ThenByDescending(task => task.Id, StringComparer.Ordinal)
                .Skip(1))
            .Select(task => task.Id)];

    private static string NameBased(string name) => NameBasedUuid.Of(Namespace, name);
}
