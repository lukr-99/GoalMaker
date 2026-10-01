package com.goalmaker.app.application.update

import com.goalmaker.app.domain.update.UpdatePolicy
import com.goalmaker.app.domain.update.UpdatePostponement
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The phone's side of a found update (docs/setup/signing-and-releases.md): one quiet notification
 * per version, the file fetched ahead in the background, and Later. It decides; the notifier, the
 * download scheduler and the files do the platform work. Nothing here installs: that waits for the
 * owner's Install. A dev build or one without a channel does nothing.
 */
class UpdateAlerts(
    private val installedVersion: String,
    private val enabled: Boolean,
    /** The update the last check found ([UpdateService.waiting]). */
    private val waiting: StateFlow<UpdateCheckResult.Available?>,
    private val memory: UpdateMemory,
    private val notifier: UpdateNotifier,
    private val downloads: UpdateDownloads,
    private val files: UpdateFiles,
    private val now: () -> Instant,
) {
    private val shownMark = MutableStateFlow(waiting.value?.takeUnless { postponed(it) })
    private val laterUntil = MutableStateFlow<Instant?>(null)

    /** The update the gear's mark stands for: the waiting one, unless Later put it off. */
    val mark: StateFlow<UpdateCheckResult.Available?> = shownMark.asStateFlow()

    /** Until when Later keeps the waiting update quiet, or null when it doesn't. */
    val postponedUntil: StateFlow<Instant?> = laterUntil.asStateFlow()

    /**
     * The app started: a notification for a version that is now installed comes down, and files
     * fetched for it go. A version still newer than this one keeps its file.
     */
    fun settle() {
        if (!enabled) return
        val notified = memory.updateNotified()
        if (notified != null && !UpdatePolicy.shouldOffer(installedVersion, notified)) {
            notifier.cancel()
            memory.setUpdateNotified(null)
        }
        val postponed = memory.updatePostponed()
        if (postponed != null && !UpdatePolicy.shouldOffer(installedVersion, postponed.version)) memory.setUpdatePostponed(null)
        val found = memory.updateFound()
        if (found == null || !UpdatePolicy.shouldOffer(installedVersion, found)) files.clean()
        refresh()
    }

    /**
     * A check (quiet or asked for) is done. One that found a trusted newer version notifies once
     * for it, unless Later holds it, and fetches its file in the background. One that got an answer
     * and found none takes the notification down and drops what was fetched. One that failed
     * changes nothing.
     */
    fun afterCheck(result: UpdateCheckResult) {
        if (!enabled) return
        when (result) {
            is UpdateCheckResult.Available -> found(result)
            is UpdateCheckResult.UpToDate, UpdateCheckResult.Untrusted -> {
                notifier.cancel()
                downloads.cancel()
                files.clean()
            }
            else -> Unit
        }
        refresh()
    }

    /** Later on [version]: its mark and notification stay away for three days, or until a newer one appears. */
    fun later(version: String) {
        if (!enabled) return
        memory.setUpdatePostponed(UpdatePostponement.later(version, now()))
        // Told again once Later runs out.
        if (memory.updateNotified() == version) memory.setUpdateNotified(null)
        notifier.cancel()
        refresh()
    }

    /** The owner opened the installer: the notification has done its job. */
    fun installing() {
        if (!enabled) return
        notifier.cancel()
    }

    private fun found(update: UpdateCheckResult.Available) {
        val version = update.manifest.version.toString()
        if (postponed(update)) {
            notifier.cancel()
        } else if (memory.updateNotified() != version && notifier.show(version)) {
            memory.setUpdateNotified(version)
        }
        downloads.schedule()
    }

    private fun postponed(update: UpdateCheckResult.Available): Boolean =
        memory.updatePostponed()?.holds(update.manifest.version.toString(), now()) == true

    private fun refresh() {
        val update = waiting.value
        val hold = update?.let { memory.updatePostponed()?.takeIf { p -> p.holds(it.manifest.version.toString(), now()) } }
        shownMark.value = if (hold == null) update else null
        laterUntil.value = hold?.until
    }
}
