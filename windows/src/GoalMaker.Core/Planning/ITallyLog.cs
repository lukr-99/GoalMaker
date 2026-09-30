namespace GoalMaker.Core.Planning;

/// <summary>
/// Tally's raw log on this PC (docs/tally.md, ADR 0013): one file a day of the windows that were in
/// front. It never syncs and never goes in a backup. The real one is on disk; the tests use their own.
/// </summary>
public interface ITallyLog
{
    /// <summary>The days that have a file.</summary>
    IReadOnlyList<DateOnly> Days();

    /// <summary>Adds a line to the day's file, making it if needed.</summary>
    void Append(DateOnly day, string line);

    /// <summary>The day's lines, or none when it has no file.</summary>
    IReadOnlyList<string> Read(DateOnly day);

    void Remove(DateOnly day);
}
