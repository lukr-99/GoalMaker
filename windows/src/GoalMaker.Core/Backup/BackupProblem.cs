namespace GoalMaker.Core.Backup;

/// <summary>
/// Why a file cannot be restored (docs/backup.md). A restore checks the whole file first, so one
/// problem means nothing at all was written.
/// </summary>
public enum BackupProblem
{
    /// <summary>Not a GoalMaker export: another kind of JSON, or not JSON at all.</summary>
    NotABackup,

    /// <summary>Written by a newer GoalMaker than this one, which would have to guess at it.</summary>
    TooNew,

    /// <summary>Somebody else's export, or a row in it belongs to somebody else.</summary>
    AnotherOwner,

    /// <summary>It carries a table this build knows nothing about.</summary>
    UnknownTable,

    /// <summary>A row has no id, so there is no saying what it is.</summary>
    RowWithoutId,
}
