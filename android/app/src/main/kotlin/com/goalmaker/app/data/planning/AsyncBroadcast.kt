package com.goalmaker.app.data.planning

import android.content.BroadcastReceiver

/**
 * Ends the extra time a receiver asked for with `goAsync()`, exactly once and never on null (GM-114).
 *
 * `goAsync()` hands the pending result out only once per broadcast and returns null when it is
 * called a second time or outside `onReceive`. Glance's own `onUpdate` already takes it, so a
 * widget receiver that calls `goAsync()` after `super.onUpdate` got null, and the `finish()` in its
 * `finally` crashed the release app. Call this inside `onReceive` (or `onUpdate`), before the
 * coroutine starts, and call [finish] in the `finally`.
 */
class AsyncBroadcast internal constructor(private var end: (() -> Unit)?) {
    /** Ends the broadcast. A second call, or a missing pending result, does nothing. */
    fun finish() {
        val action = end ?: return
        end = null
        // The system throws when a broadcast is finished twice; the work is over either way.
        runCatching { action() }
    }

    companion object {
        /** The pending result of [receiver], or one that does nothing when the system gave none. */
        fun of(receiver: BroadcastReceiver): AsyncBroadcast {
            val pending: BroadcastReceiver.PendingResult? = receiver.goAsync()
            return AsyncBroadcast(pending?.let { { it.finish() } })
        }
    }
}
