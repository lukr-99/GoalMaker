package com.goalmaker.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.about.AppInfo
import com.goalmaker.app.application.auth.AuthGateway
import com.goalmaker.app.application.auth.AuthSession
import com.goalmaker.app.application.environment.BackendEnvironment
import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.application.sync.SyncCoordinator
import com.goalmaker.app.application.update.UpdateCheckResult
import com.goalmaker.app.application.update.UpdateService
import com.goalmaker.app.domain.settings.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Appearance, account, updates, about and (dev builds) the backend switch. */
class SettingsViewModel(
    private val auth: AuthGateway,
    private val sync: SyncCoordinator,
    private val settings: SettingsStore,
    private val updates: UpdateService,
    private val appInfo: AppInfo,
    private val restartApp: () -> Unit,
) : ViewModel() {

    private val state = MutableStateFlow(
        SettingsUiState(
            themeMode = settings.themeMode.value,
            email = (auth.session.value as? AuthSession.SignedIn)?.email.orEmpty(),
            signingOut = false,
            unsyncedAtSignOut = null,
            update = UpdateUiState.Idle,
            appInfo = appInfo,
            backendUrlDraft = appInfo.backend.url,
            backendKeyDraft = appInfo.backend.publishableKey,
        ),
    )
    val uiState: StateFlow<SettingsUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(settings.themeMode, auth.session) { mode, session -> mode to session }.collect { (mode, session) ->
                state.update { it.copy(themeMode = mode, email = (session as? AuthSession.SignedIn)?.email.orEmpty()) }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)

    /**
     * Pushes what is still local, empties this device's copy, then signs out (docs/sync.md). When
     * changes can't be pushed, stops and asks, unless [discardUnsynced].
     */
    fun signOut(discardUnsynced: Boolean = false) {
        if (state.value.signingOut) return
        state.update { it.copy(signingOut = true) }
        viewModelScope.launch {
            try {
                if (sync.flushAndClear(discardUnsynced)) {
                    auth.signOut()
                    state.update { it.copy(unsyncedAtSignOut = null) }
                } else {
                    state.update { it.copy(unsyncedAtSignOut = sync.status.value.pendingChanges) }
                }
            } finally {
                state.update { it.copy(signingOut = false) }
            }
        }
    }

    fun checkForUpdates() {
        state.update { it.copy(update = UpdateUiState.Checking) }
        viewModelScope.launch {
            val result = updates.check()
            state.update { it.copy(update = UpdateUiState.Checked(result)) }
        }
    }

    fun installUpdate(available: UpdateCheckResult.Available) {
        state.update { it.copy(update = UpdateUiState.Downloading(0f)) }
        viewModelScope.launch {
            val result = updates.install(available) { progress ->
                state.update { it.copy(update = UpdateUiState.Downloading(progress)) }
            }
            state.update { it.copy(update = UpdateUiState.Installing(result)) }
        }
    }

    fun onBackendUrlChange(value: String) = state.update { it.copy(backendUrlDraft = value) }

    fun onBackendKeyChange(value: String) = state.update { it.copy(backendKeyDraft = value) }

    fun saveBackend() {
        if (!appInfo.isDevBuild) return
        val draft = BackendEnvironment(state.value.backendUrlDraft.trim(), state.value.backendKeyDraft.trim())
        settings.setBackendOverride(draft.takeIf { it.isConfigured && it != appInfo.defaultBackend })
        restartApp()
    }

    fun resetBackend() {
        if (!appInfo.isDevBuild) return
        settings.setBackendOverride(null)
        restartApp()
    }
}
