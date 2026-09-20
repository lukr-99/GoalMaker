package com.goalmaker.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.backup.RestoreReport
import com.goalmaker.app.domain.backup.BackupProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Your data (docs/backup.md, spec story 91): one file with everything, written wherever the owner
 * picks, and read back after they have seen what it would do. The file picking is the system's, so
 * no storage permission is asked for.
 */
@Composable
fun BackupCard(viewModel: SettingsViewModel, state: BackupUiState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val write = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = viewModel.exportText()
            viewModel.exported(text != null && withContext(Dispatchers.IO) { context.write(uri, text) })
        }
    }
    val read = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { viewModel.offerRestore(withContext(Dispatchers.IO) { context.read(uri) }) }
    }

    Text(
        stringResource(R.string.settings_backup_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { write.launch(viewModel.exportName()) }) {
            Text(stringResource(R.string.settings_backup_export))
        }
        OutlinedButton(onClick = { read.launch(arrayOf(MIME, "application/octet-stream", "text/plain")) }) {
            Text(stringResource(R.string.settings_backup_restore))
        }
    }

    if (state.isAsking) {
        val preview = state.preview ?: RestoreReport()
        AlertDialog(
            onDismissRequest = viewModel::clearBackup,
            title = { Text(stringResource(R.string.settings_backup_restore)) },
            text = { Text(stringResource(R.string.settings_backup_preview, preview.added, preview.updated, preview.kept)) },
            confirmButton = {
                Button(onClick = viewModel::confirmRestore) { Text(stringResource(R.string.settings_backup_restore_go)) }
            },
            dismissButton = { TextButton(onClick = viewModel::clearBackup) { Text(stringResource(R.string.plan_cancel)) } },
        )
    }

    if (state.isSaying) {
        val report = state.report
        val message = when {
            state.problem != null -> stringResource(reason(state.problem))
            report != null -> stringResource(R.string.settings_backup_restored, report.added, report.updated, report.kept)
            state.outcome == BackupOutcome.EXPORTED -> stringResource(R.string.settings_backup_exported)
            state.outcome == BackupOutcome.COULD_NOT_WRITE -> stringResource(R.string.settings_backup_not_written)
            else -> stringResource(R.string.settings_backup_not_read)
        }
        val trouble = state.problem != null || state.outcome == BackupOutcome.COULD_NOT_WRITE ||
            state.outcome == BackupOutcome.COULD_NOT_READ
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (trouble) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = viewModel::clearBackup) { Text(stringResource(R.string.settings_backup_ok)) }
    }
}

private const val MIME = "application/json"

private fun reason(problem: BackupProblem): Int = when (problem) {
    BackupProblem.NOT_A_BACKUP -> R.string.settings_backup_not_a_backup
    BackupProblem.TOO_NEW -> R.string.settings_backup_too_new
    BackupProblem.ANOTHER_OWNER -> R.string.settings_backup_another_owner
    BackupProblem.UNKNOWN_TABLE -> R.string.settings_backup_unknown_table
    BackupProblem.ROW_WITHOUT_ID -> R.string.settings_backup_broken_row
}

private fun Context.write(uri: Uri, text: String): Boolean = runCatching {
    contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
    true
}.getOrDefault(false)

private fun Context.read(uri: Uri): String? = runCatching {
    contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
}.getOrNull()
