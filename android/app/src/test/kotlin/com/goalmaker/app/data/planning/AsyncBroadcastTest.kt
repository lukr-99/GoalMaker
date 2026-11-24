package com.goalmaker.app.data.planning

import org.junit.Assert.assertEquals
import org.junit.Test

class AsyncBroadcastTest {
    @Test
    fun finishesOnce() {
        var calls = 0
        val broadcast = AsyncBroadcast { calls++ }
        broadcast.finish()
        broadcast.finish()
        assertEquals(1, calls)
    }

    @Test
    fun withoutAPendingResultFinishDoesNothing() {
        val broadcast = AsyncBroadcast(null)
        broadcast.finish()
        broadcast.finish()
    }

    @Test
    fun aThrowingFinishDoesNotEscapeAndIsNotRepeated() {
        var calls = 0
        val broadcast = AsyncBroadcast { calls++; error("already finished") }
        broadcast.finish()
        broadcast.finish()
        assertEquals(1, calls)
    }
}
