package com.goalmaker.app.ui.settings

import com.goalmaker.app.application.update.InstallResult
import com.goalmaker.app.application.update.UpdateCheckResult

/** The Updates section: idle, busy, or showing the last result. */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState

    data object Checking : UpdateUiState

    data class Checked(val result: UpdateCheckResult) : UpdateUiState

    data class Downloading(val progress: Float) : UpdateUiState

    data class Installing(val result: InstallResult) : UpdateUiState
}
