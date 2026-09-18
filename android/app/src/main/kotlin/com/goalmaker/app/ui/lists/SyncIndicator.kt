package com.goalmaker.app.ui.lists

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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

/** The sync state as a cloud in the top bar; its label is the tooltip and what TalkBack reads. Tap to sync now. */
@Composable
fun SyncIndicator(status: SyncStatus, onSyncNow: () -> Unit) {
    val label = syncStatusLabel(status).ifEmpty { stringResource(R.string.sync_not_yet) }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onSyncNow) {
            val (icon, tint) = when (status.state) {
                SyncState.SYNCING -> Icons.Outlined.CloudSync to MaterialTheme.colorScheme.onSurfaceVariant
                SyncState.OFFLINE -> Icons.Outlined.CloudOff to MaterialTheme.colorScheme.onSurfaceVariant
                SyncState.NEEDS_ATTENTION -> Icons.Outlined.ErrorOutline to MaterialTheme.colorScheme.error
                SyncState.IDLE -> Icons.Outlined.CloudDone to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(icon, contentDescription = label, tint = tint)
        }
    }
}

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
