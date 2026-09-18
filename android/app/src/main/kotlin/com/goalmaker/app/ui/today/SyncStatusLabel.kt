package com.goalmaker.app.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.goalmaker.app.R
import com.goalmaker.app.application.sync.SyncState
import com.goalmaker.app.application.sync.SyncStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** The sync indicator's text: syncing, synced at a time, offline with what waits, or a problem. */
@Composable
fun syncStatusLabel(status: SyncStatus): String = when (status.state) {
    SyncState.SYNCING -> stringResource(R.string.sync_syncing)
    SyncState.OFFLINE ->
        if (status.pendingChanges == 0) {
            stringResource(R.string.sync_offline)
        } else {
            pluralStringResource(R.plurals.sync_offline_waiting, status.pendingChanges, status.pendingChanges)
        }
    SyncState.NEEDS_ATTENTION -> stringResource(R.string.sync_needs_attention, status.problem.orEmpty())
    SyncState.IDLE -> status.lastSyncedAt
        ?.let { stringResource(R.string.sync_synced_at, clock.format(it.atZone(ZoneId.systemDefault()))) }
        .orEmpty()
}
