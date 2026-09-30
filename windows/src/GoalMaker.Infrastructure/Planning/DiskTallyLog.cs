using System.Globalization;
using GoalMaker.Core.Planning;

namespace GoalMaker.Infrastructure.Planning;

/// <summary>
/// Tally's raw log on disk (docs/tally.md): one <c>yyyy-MM-dd.jsonl</c> file a day in the tally folder
/// under the app's local data. A file that won't write or read is skipped rather than reported.
/// </summary>
public sealed class DiskTallyLog(string folder) : ITallyLog
{
    private const string Extension = ".jsonl";
    private const string DayFormat = "yyyy-MM-dd";

    public IReadOnlyList<DateOnly> Days()
    {
        try
        {
            return !Directory.Exists(folder)
                ? []
                : [.. Directory.EnumerateFiles(folder, "*" + Extension)
                    .Select(path => DateOnly.TryParseExact(Path.GetFileNameWithoutExtension(path), DayFormat, CultureInfo.InvariantCulture, DateTimeStyles.None, out var day)
                        ? day
                        : (DateOnly?)null)
                    .OfType<DateOnly>()
                    .Order()];
        }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException)
        {
            return [];
        }
    }

    public void Append(DateOnly day, string line)
    {
        try
        {
            Directory.CreateDirectory(folder);
            File.AppendAllText(PathOf(day), line + "\n");
        }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException)
        {
            // A stretch that won't write is lost; the next one tries again.
        }
    }

    public IReadOnlyList<string> Read(DateOnly day)
    {
        try
        {
            var path = PathOf(day);
            return File.Exists(path) ? [.. File.ReadAllLines(path).Where(line => line.Length > 0)] : [];
        }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException)
        {
            return [];
        }
    }

    public void Remove(DateOnly day)
    {
        try
        {
            File.Delete(PathOf(day));
        }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException)
        {
            // Still open somewhere; the next clean-up removes it.
        }
    }

    private string PathOf(DateOnly day) => Path.Combine(folder, day.ToString(DayFormat, CultureInfo.InvariantCulture) + Extension);
}
