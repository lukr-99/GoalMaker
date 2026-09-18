package com.goalmaker.app.application.update

/** What happened when installing an available update. */
sealed interface InstallResult {
    data object InstallerOpened : InstallResult

    data object DownloadCorrupted : InstallResult

    data class Failed(val detail: String) : InstallResult
}
