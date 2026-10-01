package com.goalmaker.app.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.sync.SyncStatus
import com.goalmaker.app.ui.lists.SyncIndicator
import com.goalmaker.app.ui.theme.AppTheme

/**
 * The top bar's actions, the same on every place, the Places hub included: the sync cloud, Plan
 * tomorrow, and Settings with its [SettingsMark]: an accent badge with a download arrow while an
 * update waits, and the dot for a problem nobody has read yet (docs/problems.md). Every other place
 * is pinned in the bottom bar or reached from Places (ADR 0014).
 */
@Composable
fun MainActions(
    sync: SyncStatus,
    onSyncNow: () -> Unit,
    mark: SettingsMark,
    onOpenPlan: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val problemsWaiting = stringResource(R.string.problems_waiting)
    val updateWaiting = stringResource(R.string.update_available)
    SyncIndicator(sync, onSyncNow = onSyncNow)
    IconButton(onClick = onOpenPlan) {
        Icon(Icons.Outlined.EditCalendar, contentDescription = stringResource(R.string.plan_title))
    }
    IconButton(onClick = onOpenSettings) {
        BadgedBox(
            badge = {
                if (mark.shows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (mark.problems) Badge(modifier = Modifier.semantics { contentDescription = problemsWaiting })
                        if (mark.update) {
                            Badge(
                                containerColor = AppTheme.colors.accent,
                                contentColor = AppTheme.colors.onAccent,
                                modifier = Modifier.semantics { contentDescription = updateWaiting },
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            },
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.today_settings))
        }
    }
}
