package com.goalmaker.app.application.sync

import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Runs sync when asked, one run at a time (docs/sync.md: "When sync runs"). [request] debounces
 * bursts of local writes and Realtime events; a request during a run schedules exactly one more run
 * after it. While the server can't be reached it retries on its own, backing off from [FIRST_RETRY]
 * to [LONGEST_RETRY]. Publishes the status the app's sync indicator shows.
 */
class SyncCoordinator(
    private val engine: SyncEngine,
    private val replica: Replica,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val now: () -> Instant,
    private val debounce: Duration,
) {
    private val running = Mutex()
    private val lock = Any()
    private var scheduled: Job? = null
    private var rerun = false
    private var nextRetry = FIRST_RETRY
    private val mutableStatus = MutableStateFlow(SyncStatus.INITIAL)

    val status: StateFlow<SyncStatus> = mutableStatus.asStateFlow()

    /** Asks for a sync soon; several requests within the debounce window make one run. */
    fun request() = schedule(debounce)

    /** Drops the scheduled run and any offline retry, for sign-out. */
    fun cancelScheduled() {
        synchronized(lock) {
            scheduled?.cancel()
            scheduled = null
            nextRetry = FIRST_RETRY
        }
    }

    /** Syncs now. If a run is in progress, one more run follows it and this waits for both. */
    suspend fun syncNow(): SyncReport {
        if (!running.tryLock()) {
            synchronized(lock) { rerun = true }
            running.lock()
        }

        try {
            var report: SyncReport
            do {
                synchronized(lock) { rerun = false }
                mutableStatus.value = mutableStatus.value.copy(state = SyncState.SYNCING)
                var pending = mutableStatus.value.pendingChanges
                report = try {
                    withContext(io) {
                        engine.run().also { pending = replica.pendingCount() }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // A bug or a broken replica must not take the app down; show it instead.
                    SyncReport(pushed = 0, rejected = 1, pulled = 0, offline = false, problem = error.message ?: error.toString())
                }

                mutableStatus.value = when {
                    report.offline -> mutableStatus.value.copy(
                        state = SyncState.OFFLINE,
                        pendingChanges = pending,
                        problem = report.problem,
                    )
                    report.rejected > 0 -> SyncStatus(SyncState.NEEDS_ATTENTION, now(), pending, report.problem)
                    else -> SyncStatus(SyncState.IDLE, now(), pending, problem = null)
                }
            } while (synchronized(lock) { rerun })

            retryIfOffline(report)
            return report
        } finally {
            running.unlock()
        }
    }

    /**
     * For sign-out: pushes what it can, then empties the replica. Returns false, and keeps the data,
     * when changes are still unsynced and [discardUnsynced] is false.
     */
    suspend fun flushAndClear(discardUnsynced: Boolean): Boolean {
        syncNow()
        if (withContext(io) { replica.pendingCount() } > 0 && !discardUnsynced) {
            return false
        }

        withContext(io) { replica.clearAll() }
        cancelScheduled()
        mutableStatus.value = SyncStatus.INITIAL
        return true
    }

    // Replaces whatever run was scheduled: a newer request or retry always wins.
    private fun schedule(wait: Duration) {
        synchronized(lock) {
            scheduled?.cancel()
            scheduled = scope.launch {
                delay(wait)
                // Detached, so a newer request cancels only the wait, never a run in progress.
                scope.launch { syncNow() }
            }
        }
    }

    private fun retryIfOffline(report: SyncReport) {
        val wait = synchronized(lock) {
            if (!report.offline) {
                nextRetry = FIRST_RETRY
                return
            }

            nextRetry.also { nextRetry = minOf(nextRetry * 2, LONGEST_RETRY) }
        }

        schedule(wait)
    }

    companion object {
        val FIRST_RETRY = 15.seconds
        val LONGEST_RETRY = 5.minutes
    }
}
