package com.goalmaker.app.ui.connector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.R
import com.goalmaker.app.ui.theme.AppTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Settings, Claude connector (docs/connector.md): what the connector is, the link's state, and
 * Create, Make a new link and Revoke. A new link is shown once, with Copy and where to paste it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConnectorScreen(viewModel: ConnectorViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val clipboard = LocalClipboardManager.current
    val times = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", LocalConfiguration.current.locales[0])
    var confirming by remember { mutableStateOf<ConnectorConfirm?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { ScreenTitle(stringResource(R.string.connector_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.density.pagePadding.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.connector_intro), style = MaterialTheme.typography.bodyLarge)

            state.newUrl?.let { url ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.connector_new_title), style = MaterialTheme.typography.titleMedium)
                        SelectionContainer { Text(url, style = MaterialTheme.typography.bodyMedium) }
                        Text(
                            stringResource(R.string.connector_new_steps),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textMuted,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { clipboard.setText(AnnotatedString(url)) }) {
                                Text(stringResource(R.string.connector_copy))
                            }
                            TextButton(onClick = viewModel::hideNewLink) { Text(stringResource(R.string.connector_done)) }
                        }
                    }
                }
            }

            when {
                !state.loaded -> Text(stringResource(R.string.connector_loading), color = AppTheme.colors.textMuted)
                state.unavailable -> {
                    Text(stringResource(R.string.connector_offline), color = AppTheme.colors.textMuted)
                    OutlinedButton(onClick = viewModel::refresh, enabled = !state.busy) {
                        Text(stringResource(R.string.connector_retry))
                    }
                }
                state.active == null -> {
                    Text(stringResource(R.string.connector_none), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = viewModel::create, enabled = !state.busy) {
                        Text(stringResource(R.string.connector_create))
                    }
                }
                else -> {
                    val link = state.active!!
                    val zone = ZoneId.systemDefault()
                    Text(
                        stringResource(R.string.connector_active, link.createdAt.atZone(zone).format(times)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        link.lastUsedAt?.let { stringResource(R.string.connector_last_used, it.atZone(zone).format(times)) }
                            ?: stringResource(R.string.connector_never_used),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.textMuted,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { confirming = ConnectorConfirm.ROTATE }, enabled = !state.busy) {
                            Text(stringResource(R.string.connector_rotate))
                        }
                        OutlinedButton(onClick = { confirming = ConnectorConfirm.REVOKE }, enabled = !state.busy) {
                            Text(stringResource(R.string.connector_revoke), color = AppTheme.colors.danger)
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.connector_risk),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textMuted,
            )
        }
    }

    confirming?.let { action ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = {
                Text(stringResource(if (action == ConnectorConfirm.ROTATE) R.string.connector_rotate else R.string.connector_revoke))
            },
            text = {
                Text(stringResource(if (action == ConnectorConfirm.ROTATE) R.string.connector_rotate_warning else R.string.connector_revoke_warning))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    if (action == ConnectorConfirm.ROTATE) viewModel.create() else viewModel.revoke()
                }) { Text(stringResource(if (action == ConnectorConfirm.ROTATE) R.string.connector_rotate else R.string.connector_revoke)) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.areas_cancel)) } },
        )
    }
}
