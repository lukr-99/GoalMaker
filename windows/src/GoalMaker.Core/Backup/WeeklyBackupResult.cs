namespace GoalMaker.Core.Backup;

/// <summary>What the weekly export did when it last looked (spec, story 92).</summary>
public enum WeeklyBackupResult
{
    /// <summary>No folder chosen, so there is nothing to do.</summary>
    Off,

    /// <summary>The last one is less than a week old.</summary>
    NotDue,

    /// <summary>The folder is gone, so the weekly export turned itself off.</summary>
    FolderGone,

    /// <summary>Nobody is signed in, so there is nothing to write.</summary>
    NothingToWrite,

    /// <summary>The folder is there but the file could not be written.</summary>
    CouldNotWrite,

    /// <summary>One was written.</summary>
    Written,
}
