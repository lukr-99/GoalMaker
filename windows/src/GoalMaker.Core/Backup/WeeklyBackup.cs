using GoalMaker.Core.Settings;

namespace GoalMaker.Core.Backup;

/// <summary>
/// The weekly export into the folder the owner chose (spec, story 92). It runs at start-up and
/// after each sync; a folder that has gone away turns it off and says so, rather than failing
/// quietly every week. The file writing itself is a seam, so this is testable without a disk.
/// </summary>
public sealed class WeeklyBackup(
    BackupService backup,
    ISettingsStore settings,
    TimeProvider time,
    IBackupFolder folder)
{
    /// <summary>
    /// Writes one if it is due. Returns what happened, so Settings can say when the last one was
    /// written and why one was not.
    /// </summary>
    public WeeklyBackupResult Run()
    {
        var chosen = settings.WeeklyBackupFolder;
        if (string.IsNullOrWhiteSpace(chosen))
        {
            return WeeklyBackupResult.Off;
        }

        if (!WeeklyBackupRules.IsDue(settings.WeeklyBackupWritten, time.GetUtcNow()))
        {
            return WeeklyBackupResult.NotDue;
        }

        if (!folder.Exists(chosen))
        {
            settings.WeeklyBackupFolder = null;
            return WeeklyBackupResult.FolderGone;
        }

        if (backup.Export() is not { } text)
        {
            return WeeklyBackupResult.NothingToWrite;
        }

        var day = DateOnly.FromDateTime(time.GetLocalNow().DateTime).ToString("yyyy-MM-dd");
        if (!folder.Write(chosen, BackupRules.FileName(day), text))
        {
            return WeeklyBackupResult.CouldNotWrite;
        }

        settings.WeeklyBackupWritten = time.GetUtcNow();
        foreach (var old in WeeklyBackupRules.Prune(folder.Exports(chosen)))
        {
            folder.Remove(chosen, old);
        }

        return WeeklyBackupResult.Written;
    }
}
