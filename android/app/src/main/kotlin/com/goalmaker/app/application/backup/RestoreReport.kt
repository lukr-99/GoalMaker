package com.goalmaker.app.application.backup

/**
 * What a restore did, or would do, table by table (docs/backup.md): rows that were not here,
 * rows the file was newer for, and rows the file was older for, which a restore leaves alone.
 */
data class RestoreReport(
    val added: Int = 0,
    val updated: Int = 0,
    val kept: Int = 0,
) {
    val total: Int get() = added + updated + kept

    val changed: Int get() = added + updated

    operator fun plus(other: RestoreReport) = RestoreReport(
        added = added + other.added,
        updated = updated + other.updated,
        kept = kept + other.kept,
    )
}
