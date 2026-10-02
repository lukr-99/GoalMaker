package com.goalmaker.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.goalmaker.app.R
import com.goalmaker.app.application.backup.RestoreReport
import com.goalmaker.app.domain.backup.BackupProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Your data (docs/backup.md, spec story 91): Export writes one file with everything wherever the
 * owner picks, and Restore, in the danger zone at the end, reads one back after the owner has seen
 * what it would do and agreed, with Cancel focused. A result replaces the hint of the row it
 * belongs to. The file picking is the system's, so no storage permission is asked for.
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

    val exportResult = when (state.outcome) {
        BackupOutcome.EXPORTED -> stringResource(R.string.settings_backup_exported)
        BackupOutcome.COULD_NOT_WRITE -> stringResource(R.string.settings_backup_not_written)
        else -> null
    }
    val report = state.report
    val problem = state.problem
    val restoreResult = when {
        problem != null -> stringResource(reason(problem))
        report != null -> stringResource(R.string.settings_backup_restored, report.added, report.updated, report.kept)
        state.outcome == BackupOutcome.COULD_NOT_READ -> stringResource(R.string.settings_backup_not_read)
        else -> null
    }
    ButtonRow(
        title = stringResource(R.string.settings_backup_export_title),
        hint = stringResource(R.string.settings_backup_export_hint),
        button = stringResource(R.string.settings_backup_export),
        onClick = { write.launch(viewModel.exportName()) },
        result = exportResult,
        trouble = state.outcome == BackupOutcome.COULD_NOT_WRITE,
    )
    DangerZone {
        DangerRow(
            title = stringResource(R.string.settings_backup_restore_title),
            hint = stringResource(R.string.settings_backup_restore_hint),
            button = stringResource(R.string.settings_backup_restore),
            onClick = { read.launch(arrayOf(MIME, "application/octet-stream", "text/plain")) },
            result = restoreResult,
            trouble = problem != null || state.outcome == BackupOutcome.COULD_NOT_READ,
        )
    }

    if (state.isAsking) {
        val preview = state.preview ?: RestoreReport()
        ConfirmDialog(
            title = stringResource(R.string.settings_backup_restore_ask),
            text = stringResource(R.string.settings_backup_preview, preview.added, preview.updated, preview.kept),
            confirm = stringResource(R.string.settings_backup_restore_go),
            onConfirm = viewModel::confirmRestore,
            onDismiss = viewModel::clearBackup,
        )
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
