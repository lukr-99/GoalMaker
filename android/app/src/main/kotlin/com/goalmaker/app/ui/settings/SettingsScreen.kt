package com.goalmaker.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.update.InstallResult
import com.goalmaker.app.application.update.UpdateCheckResult
import com.goalmaker.app.domain.settings.ThemeMode

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section(stringResource(R.string.settings_appearance)) {
                ThemeModeSelector(selected = state.themeMode, onSelect = viewModel::setThemeMode)
            }
            Section(stringResource(R.string.settings_account)) {
                Text(state.email, style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = viewModel::signOut) { Text(stringResource(R.string.settings_sign_out)) }
            }
            Section(stringResource(R.string.settings_updates)) {
                UpdatesContent(state.update, viewModel)
            }
            Section(stringResource(R.string.settings_about)) {
                Text(stringResource(R.string.settings_version, state.appInfo.versionName))
                Text(
                    stringResource(R.string.settings_backend, state.appInfo.backend.url),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.appInfo.isDevBuild) {
                Section(stringResource(R.string.settings_developer)) {
                    Text(
                        stringResource(R.string.settings_backend_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.backendUrlDraft,
                        onValueChange = viewModel::onBackendUrlChange,
                        label = { Text(stringResource(R.string.settings_backend_url)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.backendKeyDraft,
                        onValueChange = viewModel::onBackendKeyChange,
                        label = { Text(stringResource(R.string.settings_backend_key)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::saveBackend) { Text(stringResource(R.string.settings_backend_save)) }
                        TextButton(onClick = viewModel::resetBackend) { Text(stringResource(R.string.settings_backend_reset)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun ThemeModeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
        ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
        ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, (mode, label) ->
            ToggleButton(
                checked = selected == mode,
                onCheckedChange = { onSelect(mode) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun UpdatesContent(update: UpdateUiState, viewModel: SettingsViewModel) {
    when (update) {
        UpdateUiState.Idle -> Unit
        UpdateUiState.Checking -> {
            LoadingIndicator(modifier = Modifier.size(40.dp))
            Text(stringResource(R.string.settings_update_checking))
        }
        is UpdateUiState.Checked -> CheckResult(update.result, viewModel)
        is UpdateUiState.Downloading -> {
            LinearWavyProgressIndicator(progress = { update.progress }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.settings_update_downloading, (update.progress * 100).toInt()))
        }
        is UpdateUiState.Installing -> Text(
            when (val result = update.result) {
                InstallResult.InstallerOpened -> stringResource(R.string.settings_update_opened)
                InstallResult.DownloadCorrupted -> stringResource(R.string.settings_update_corrupted)
                is InstallResult.Failed -> stringResource(R.string.settings_update_failed, result.detail)
            },
        )
    }
    val busy = update is UpdateUiState.Checking || update is UpdateUiState.Downloading
    OutlinedButton(onClick = viewModel::checkForUpdates, enabled = !busy) {
        Text(stringResource(R.string.settings_check_updates))
    }
    Text(
        stringResource(R.string.settings_update_manual),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CheckResult(result: UpdateCheckResult, viewModel: SettingsViewModel) {
    val message = when (result) {
        UpdateCheckResult.NotConfigured -> stringResource(R.string.settings_update_not_configured)
        UpdateCheckResult.DevelopmentBuild -> stringResource(R.string.settings_update_dev_build)
        is UpdateCheckResult.UpToDate -> stringResource(R.string.settings_update_up_to_date, result.latest)
        is UpdateCheckResult.Available -> stringResource(R.string.settings_update_available, result.manifest.version.toString())
        UpdateCheckResult.Untrusted -> stringResource(R.string.settings_update_untrusted)
        is UpdateCheckResult.Failed -> stringResource(R.string.settings_update_failed, result.detail)
    }
    Text(message, style = MaterialTheme.typography.bodyLarge)
    if (result is UpdateCheckResult.Available) {
        Button(onClick = { viewModel.installUpdate(result) }) {
            Text(stringResource(R.string.settings_install_update, result.manifest.version.toString()))
        }
    }
}
