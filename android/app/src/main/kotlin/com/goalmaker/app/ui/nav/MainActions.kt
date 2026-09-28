package com.goalmaker.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.goalmaker.app.R
import com.goalmaker.app.application.sync.SyncStatus
import com.goalmaker.app.ui.lists.SyncIndicator

/**
 * The top bar's actions, the same on every place: the sync cloud, Plan tomorrow, and Settings with
 * its mark for a problem nobody has read yet (docs/problems.md). Every other place is pinned in the
 * bottom bar or reached from Places (ADR 0014).
 */
@Composable
fun MainActions(
    sync: SyncStatus,
    onSyncNow: () -> Unit,
    hasProblems: Boolean,
    onOpenPlan: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val waiting = stringResource(R.string.problems_waiting)
    SyncIndicator(sync, onSyncNow = onSyncNow)
    IconButton(onClick = onOpenPlan) {
        Icon(Icons.Outlined.EditCalendar, contentDescription = stringResource(R.string.plan_title))
    }
    IconButton(onClick = onOpenSettings) {
        BadgedBox(
            badge = { if (hasProblems) Badge(modifier = Modifier.semantics { contentDescription = waiting }) },
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.today_settings))
        }
    }
}
