package com.goalmaker.app.domain.backup

/**
 * Why a file cannot be restored (docs/backup.md). A restore checks the whole file first, so one
 * problem means nothing at all was written.
 */
enum class BackupProblem {
    /** Not a GoalMaker export: another kind of JSON, or not JSON at all. */
    NOT_A_BACKUP,

    /** Written by a newer GoalMaker than this one, which would have to guess at it. */
    TOO_NEW,

    /** Somebody else's export, or a row in it belongs to somebody else. */
    ANOTHER_OWNER,

    /** It carries a table this build knows nothing about. */
    UNKNOWN_TABLE,

    /** A row has no id, so there is no saying what it is. */
    ROW_WITHOUT_ID,
    ;

    /** The name this problem has in contracts/vectors/backup.json. */
    val key: String
        get() = name.split('_').mapIndexed { index, part ->
            if (index == 0) part.lowercase() else part.lowercase().replaceFirstChar(Char::uppercase)
        }.joinToString("")
}
