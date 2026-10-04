package com.goalmaker.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.about.AppInfo
import com.goalmaker.app.application.auth.AuthGateway
import com.goalmaker.app.application.auth.AuthSession
import com.goalmaker.app.application.auth.UnlockAvailability
import com.goalmaker.app.application.backup.BackupService
import com.goalmaker.app.application.environment.BackendEnvironment
import com.goalmaker.app.application.planning.WhyFrequency
import com.goalmaker.app.application.planning.DndBreakthrough
import com.goalmaker.app.application.planning.ReminderService
import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.application.sync.SyncCoordinator
import com.goalmaker.app.application.update.AutoUpdateCheck
import com.goalmaker.app.application.update.InstallResult
import com.goalmaker.app.application.update.UpdateAlerts
import com.goalmaker.app.application.update.UpdateCheckResult
import com.goalmaker.app.application.update.UpdateService
import com.goalmaker.app.domain.backup.BackupProblem
import com.goalmaker.app.domain.backup.BackupRules
import com.goalmaker.app.domain.design.DesignTokens
import com.goalmaker.app.domain.planning.QuietHours
import com.goalmaker.app.domain.settings.ReduceMotion
import com.goalmaker.app.domain.settings.ThemeMode
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Appearance, account, updates, about and (dev builds) the backend switch. */
class SettingsViewModel(
    private val auth: AuthGateway,
    private val sync: SyncCoordinator,
    private val settings: SettingsStore,
    private val reminders: ReminderService,
    private val io: CoroutineDispatcher,
    private val design: DesignTokens,
    private val updates: UpdateService,
    /** Check for updates goes through the quiet check too, so the last time a check got through is kept. */
    private val updateChecks: AutoUpdateCheck,
    /** Later, and whether the waiting update is put off. */
    private val updateAlerts: UpdateAlerts,
    /** Where a release can be downloaded by hand (spec, story 94); null when there is no channel. */
    val releasesPage: String? = null,
    private val appInfo: AppInfo,
    private val backup: BackupService,
    private val requestSync: () -> Unit,
    private val restartApp: () -> Unit,
    private val unlockAvailability: () -> UnlockAvailability = { UnlockAvailability.UNAVAILABLE },
    private val appLockTurned: (Boolean) -> Unit = {},
    private val importantDnd: () -> DndBreakthrough = { DndBreakthrough.NOT_ALLOWED },
) : ViewModel() {

    private val state = MutableStateFlow(
        SettingsUiState(
            appearance = settings.appearance.value,
            dayStartHour = settings.dayStartHour.value,
            quietHours = settings.quietHours.value,
            planTomorrowReminder = settings.planTomorrowReminder.value,
            weeklyReviewReminder = settings.weeklyReviewReminder.value,
            weeklyReviewWeekday = settings.weeklyReviewWeekday.value,
            monthlyReviewReminder = settings.monthlyReviewReminder.value,
            wantsReadyReminder = settings.wantsReadyReminder.value,
            whyReminder = settings.whyReminder.value,
            themeId = design.theme(settings.appearance.value.themeId).id,
            themes = design.themes,
            email = (auth.session.value as? AuthSession.SignedIn)?.email.orEmpty(),
            appLock = settings.appLock.value,
            unlock = unlockAvailability(),
            importantDnd = importantDnd(),
            signingOut = false,
            unsyncedAtSignOut = null,
            update = UpdateUiState.Idle,
            appInfo = appInfo,
            backendUrlDraft = appInfo.backend.url,
            backendKeyDraft = appInfo.backend.publishableKey,
        ),
    )
    val uiState: StateFlow<SettingsUiState> = state.asStateFlow()

    /** Which controls just saved, for the Saved mark next to each (docs/design/spec.md, Settings). */
    val saved = SavedMarks()

    init {
        // Kept by the update service, so the row is still there when Settings opens again.
        viewModelScope.launch {
            updates.waiting.collect { waiting ->
                state.update { it.copy(waitingUpdate = waiting) }
                // Measuring the file reads it, so off the main thread.
                val downloaded = waiting != null && withContext(io) { updates.ready(waiting) != null }
                state.update { it.copy(updateDownloaded = downloaded) }
            }
        }
        viewModelScope.launch {
            updateAlerts.postponedUntil.collect { until -> state.update { it.copy(updatePostponedUntil = until) } }
        }
        viewModelScope.launch {
            updateChecks.lastChecked.collect { at -> state.update { it.copy(updatesCheckedAt = at) } }
        }
        viewModelScope.launch {
            settings.dayStartHour.collect { hour -> state.update { it.copy(dayStartHour = hour) } }
        }
        viewModelScope.launch {
            settings.quietHours.collect { window -> state.update { it.copy(quietHours = window) } }
        }
        viewModelScope.launch {
            settings.appLock.collect { on -> state.update { it.copy(appLock = on) } }
        }
        viewModelScope.launch {
            settings.planTomorrowReminder.collect { time -> state.update { it.copy(planTomorrowReminder = time) } }
        }
        viewModelScope.launch {
            settings.weeklyReviewReminder.collect { time -> state.update { it.copy(weeklyReviewReminder = time) } }
        }
        viewModelScope.launch {
            settings.weeklyReviewWeekday.collect { day -> state.update { it.copy(weeklyReviewWeekday = day) } }
        }
        viewModelScope.launch {
            settings.monthlyReviewReminder.collect { time -> state.update { it.copy(monthlyReviewReminder = time) } }
        }
        viewModelScope.launch {
            settings.wantsReadyReminder.collect { time -> state.update { it.copy(wantsReadyReminder = time) } }
        }
        viewModelScope.launch {
            settings.whyReminder.collect { frequency -> state.update { it.copy(whyReminder = frequency) } }
        }
        viewModelScope.launch {
            combine(settings.appearance, auth.session) { appearance, session -> appearance to session }
                .collect { (appearance, session) ->
                    state.update {
                        it.copy(
                            appearance = appearance,
                            themeId = design.theme(appearance.themeId).id,
                            email = (session as? AuthSession.SignedIn)?.email.orEmpty(),
                        )
                    }
                }
        }
    }

    /**
     * Turns the lock on or off (docs/sign-in.md). Turning it on takes hold the next time the app
     * leaves the screen, so Settings does not lock itself behind the owner as they tap it.
     */
    fun setAppLock(on: Boolean) {
        if (on && unlockAvailability() != UnlockAvailability.READY) return
        settings.setAppLock(on)
        appLockTurned(on)
        saved.mark(SettingKey.APP_LOCK)
    }

    /** Asks the phone again what it can do, in case the owner has just set a fingerprint up. */
    fun checkUnlock() = state.update { it.copy(unlock = unlockAvailability()) }

    /** Asks the phone again whether important reminders ring through Do Not Disturb, after its settings page. */
    fun checkImportantDnd() = state.update { it.copy(importantDnd = importantDnd()) }

    fun setTheme(id: String) {
        settings.updateAppearance { it.copy(themeId = id) }
        saved.mark(SettingKey.THEME)
    }

    fun setDayStartHour(hour: Int) {
        settings.setDayStartHour(hour)
        saved.mark(SettingKey.DAY_START)
    }

    /** Quiet hours hold ordinary reminders back (docs/reminders.md); the alarm is armed again. */
    fun setQuietHours(window: QuietHours) {
        val before = state.value.quietHours
        settings.setQuietHours(window)
        viewModelScope.launch(io) { reminders.rearm() }
        // Switching quiet hours off says so in its own row; a time typed in marks its own field.
        if (!window.off && window.start != before.start) saved.mark(SettingKey.QUIET_START)
        if (!window.off && window.end != before.end) saved.mark(SettingKey.QUIET_END)
    }

    /** When the weekly review reminder rings, and on which weekday (docs/reviews.md). */
    fun setWeeklyReviewReminder(time: LocalTime?) {
        val before = state.value.weeklyReviewReminder
        settings.setWeeklyReviewReminder(time)
        rearm()
        markReminder(before, time, SettingKey.WEEKLY_REVIEW, SettingKey.WEEKLY_REVIEW_TIME)
    }

    fun setWeeklyReviewWeekday(weekday: Int) {
        settings.setWeeklyReviewWeekday(weekday)
        rearm()
        saved.mark(SettingKey.WEEKLY_REVIEW_DAY)
    }

    /** When the notification for wants that became ready rings; the alarm is armed again. */
    /** How often the why reminder shows a life goal (docs/life-goals.md). */
    fun setWhyReminder(frequency: WhyFrequency) {
        settings.setWhyReminder(frequency)
        saved.mark(SettingKey.WHY_REMINDER)
        rearm()
    }

    fun setWantsReadyReminder(time: LocalTime?) {
        val before = state.value.wantsReadyReminder
        settings.setWantsReadyReminder(time)
        rearm()
        markReminder(before, time, SettingKey.WANTS_READY, SettingKey.WANTS_READY_TIME)
    }

    /** When the monthly review reminder rings, on the first day of a month. */
    fun setMonthlyReviewReminder(time: LocalTime?) {
        val before = state.value.monthlyReviewReminder
        settings.setMonthlyReviewReminder(time)
        rearm()
        markReminder(before, time, SettingKey.MONTHLY_REVIEW, SettingKey.MONTHLY_REVIEW_TIME)
    }

    /** Moves or switches off the evening Plan tomorrow reminder; the alarm is armed again. */
    fun setPlanTomorrowReminder(time: LocalTime?) {
        val before = state.value.planTomorrowReminder
        settings.setPlanTomorrowReminder(time)
        viewModelScope.launch(io) { reminders.rearm() }
        markReminder(before, time, SettingKey.PLAN_REMINDER, SettingKey.PLAN_REMINDER_TIME)
    }

    fun setThemeMode(mode: ThemeMode) {
        settings.updateAppearance { it.copy(mode = mode) }
        saved.mark(SettingKey.MODE)
    }

    fun setPureBlack(enabled: Boolean) {
        settings.updateAppearance { it.copy(pureBlack = enabled) }
        saved.mark(SettingKey.PURE_BLACK)
    }

    fun setReduceMotion(choice: ReduceMotion) {
        settings.updateAppearance { it.copy(reduceMotion = choice) }
        saved.mark(SettingKey.REDUCE_MOTION)
    }

    fun setCompletionSound(enabled: Boolean) {
        settings.updateAppearance { it.copy(completionSound = enabled) }
        saved.mark(SettingKey.COMPLETION_SOUND)
    }

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
            val result = updateChecks.checkNow()
            state.update { it.copy(update = UpdateUiState.Checked(result)) }
        }
    }

    /**
     * Opens the installer for [available]: at once when its APK was fetched ahead and still matches,
     * otherwise after a verified download. Android asks the owner to confirm either way.
     */
    fun installUpdate(available: UpdateCheckResult.Available) {
        state.update { it.copy(update = UpdateUiState.Downloading(0f)) }
        viewModelScope.launch {
            val result = updates.install(available) { progress ->
                state.update { it.copy(update = UpdateUiState.Downloading(progress)) }
            }
            if (result == InstallResult.InstallerOpened) updateAlerts.installing()
            state.update { it.copy(update = UpdateUiState.Installing(result)) }
        }
    }

    /**
     * Install, from the notification: the waiting update, or, in a process that has not checked yet,
     * whatever a check finds now.
     */
    fun installRequested() {
        val waiting = updates.waiting.value
        if (waiting != null) {
            installUpdate(waiting)
            return
        }
        state.update { it.copy(update = UpdateUiState.Checking) }
        viewModelScope.launch {
            val result = updateChecks.checkNow()
            if (result is UpdateCheckResult.Available) installUpdate(result) else state.update { it.copy(update = UpdateUiState.Checked(result)) }
        }
    }

    /** Later: the mark and the notification for [available] stay away for three days, or until a newer version. */
    fun postponeUpdate(available: UpdateCheckResult.Available) = updateAlerts.later(available.manifest.version.toString())

    /** The export's text, for the file the owner picked, or null when there is nothing to write. */
    suspend fun exportText(): String? = withContext(io) { backup.export() }

    /** The name the file is offered under: goalmaker-<today>.json (docs/backup.md). */
    fun exportName(): String = BackupRules.fileName(LocalDate.now().toString())

    /** Says what the export did, or that the file could not be written. */
    fun exported(written: Boolean) = state.update {
        it.copy(backup = BackupUiState(outcome = if (written) BackupOutcome.EXPORTED else BackupOutcome.COULD_NOT_WRITE))
    }

    /** Reads a picked file and offers what restoring it would do; the owner confirms after this. */
    fun offerRestore(text: String?) {
        if (text == null) {
            state.update { it.copy(backup = BackupUiState(outcome = BackupOutcome.COULD_NOT_READ)) }
            return
        }
        viewModelScope.launch(io) {
            val problem = backup.check(text)
            val preview = if (problem == null) backup.preview(text) else null
            state.update { it.copy(backup = BackupUiState(pending = if (problem == null) text else null, preview = preview, problem = problem)) }
        }
    }

    /** Restores the file the owner just confirmed. */
    fun confirmRestore() {
        val text = state.value.backup.pending ?: return
        viewModelScope.launch(io) {
            val report = backup.restore(text, requestSync)
            state.update {
                it.copy(
                    backup = if (report == null) {
                        BackupUiState(problem = backup.check(text), outcome = BackupOutcome.COULD_NOT_READ)
                    } else {
                        BackupUiState(outcome = BackupOutcome.RESTORED, report = report)
                    },
                )
            }
        }
    }

    /** Closes whatever the backup card is saying. */
    fun clearBackup() = state.update { it.copy(backup = BackupUiState()) }

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

    /** Dev builds only: sign in and sync, or keep everything on the phone; takes effect on a restart. */
    fun setDevSignIn(on: Boolean) {
        if (!appInfo.isDevBuild) return
        settings.setDevSignIn(on)
        restartApp()
    }

    // A reminder turned on or off marks its switch; a new time marks its slider.
    private fun markReminder(before: LocalTime?, after: LocalTime?, toggle: SettingKey, time: SettingKey) {
        saved.mark(if ((before == null) != (after == null)) toggle else time)
    }

    // Every reminder setting ends the same way: the one alarm is armed for whatever comes first.
    private fun rearm() {
        viewModelScope.launch(io) { reminders.rearm() }
    }
}
