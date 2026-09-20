using System.IO;
using GoalMaker.Core.Backup;

namespace GoalMaker.Infrastructure.Backup;

/// <summary>The weekly export's folder on disk (docs/backup.md). A folder that is gone says so.</summary>
public sealed class DiskBackupFolder : IBackupFolder
{
    public bool Exists(string folder) => Directory.Exists(folder);

    public bool Write(string folder, string name, string text)
    {
        try
        {
            File.WriteAllText(Path.Combine(folder, name), text);
            return true;
        }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException)
        {
            return false;
        }
    }

    public IReadOnlyList<string> Exports(string folder)
    {
        try
        {
            return [.. Directory.EnumerateFiles(folder, "goalmaker-*.json").Select(Path.GetFileName).OfType<string>()];
        }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException)
        {
            return [];
        }
    }

    public void Remove(string folder, string name)
    {
        try
        {
            File.Delete(Path.Combine(folder, name));
        }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException)
        {
            // An old export that will not go is not worth telling the owner about.
        }
    }
}
