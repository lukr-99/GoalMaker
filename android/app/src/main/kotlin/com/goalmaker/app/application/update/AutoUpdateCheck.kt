package com.goalmaker.app.application.update

import com.goalmaker.app.application.settings.SettingsStore
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * The quiet check for updates: a little after the app comes to the front, at most once a day, so
 * the mark on the gear shows without the owner asking. It only checks and verifies the signed
 * manifest through [UpdateService]; downloading and installing stay behind the owner's Install. A
 * failure says nothing, and a build without a channel (or a dev build) never checks. The last check
 * that reached the channel is kept in the settings, so starts don't ask GitHub again within the day.
 */
class AutoUpdateCheck(
    private val updates: UpdateService,
    private val settings: SettingsStore,
    private val now: () -> Instant,
    private val interval: Duration = DAILY,
    private val startDelay: kotlin.time.Duration = START_DELAY,
    /** Told about every check, quiet or asked for: the notification and the background download follow it. */
    private val afterCheck: (UpdateCheckResult) -> Unit = {},
) {
    private val running = AtomicBoolean(false)

    /** When a check last reached the channel, or null before the first. */
    val lastChecked: StateFlow<Instant?> = settings.updatesCheckedAt

    /**
     * Whether the quiet check should run now: never without a channel; otherwise when no check has
     * reached the channel for [interval] (or the clock went back past the last one), or when the
     * last one found an update that this run of the app has not shown yet.
     */
    fun isDue(): Boolean {
        if (!updates.canCheck) return false
        val last = settings.updatesCheckedAt.value ?: return true
        val at = now()
        if (Duration.between(last, at) >= interval || at.isBefore(last)) return true
        return settings.updateFound() != null && updates.waiting.value == null
    }

    /** The app came to the front: wait a moment so the start is not held up, then check if one is due. */
    suspend fun afterStart(): UpdateCheckResult? {
        if (!updates.canCheck) return null
        delay(startDelay)
        return checkIfDue()
    }

    /**
     * The quiet check: runs when [isDue] and no other quiet check is running, and returns what it
     * found, or null when it did not run. It never throws for a failure.
     */
    suspend fun checkIfDue(): UpdateCheckResult? {
        if (!isDue() || !running.compareAndSet(false, true)) return null
        return try {
            checkNow()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            UpdateCheckResult.Failed(error.message ?: error::class.simpleName.orEmpty())
        } finally {
            running.set(false)
        }
    }

    /**
     * A check now, as Check for updates asks for. A check that reached the channel is written down
     * with what it found; a failed one leaves the last time as it was, so the next start tries again.
     */
    suspend fun checkNow(): UpdateCheckResult {
        val result = updates.check()
        if (result is UpdateCheckResult.UpToDate || result is UpdateCheckResult.Available || result == UpdateCheckResult.Untrusted) {
            settings.setUpdateCheck(now(), (result as? UpdateCheckResult.Available)?.manifest?.version?.toString())
        }
        afterCheck(result)
        return result
    }

    companion object {
        /** How often the app looks: once a day. */
        val DAILY: Duration = Duration.ofDays(1)

        /** How long after coming to the front the app waits before it looks. */
        val START_DELAY: kotlin.time.Duration = 5.seconds
    }
}
