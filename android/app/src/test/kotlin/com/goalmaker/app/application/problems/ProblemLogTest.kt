package com.goalmaker.app.application.problems

import com.goalmaker.app.domain.problems.ProblemRules
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a problem shows (M6-08, docs/problems.md): in Settings, with a quiet mark on the gear, and
 * gone again when it comes right by itself.
 */
class ProblemLogTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-21T10:00:00Z"), ZoneOffset.UTC)
    private val log = ProblemLog(clock)

    @Test
    fun `a push that would not go shows in settings and marks the gear`() {
        log.report(ProblemRules.SYNC, "23502: null value in column \"priority\"")

        val problem = log.problems.value.single()
        assertEquals(ProblemRules.SYNC, problem.kind)
        assertEquals("23502: null value in column \"priority\"", problem.detail)
        assertTrue(problem.unread)
        assertTrue(ProblemRules.isMarked(log.problems.value))
    }

    @Test
    fun `opening settings takes the mark off but leaves the problem`() {
        log.report(ProblemRules.SYNC, "the push that would not go")

        log.read()

        assertFalse(log.problems.value.single().unread)
        assertFalse(ProblemRules.isMarked(log.problems.value))
    }

    @Test
    fun `a sync that works later clears it with nobody doing anything`() {
        log.report(ProblemRules.SYNC, "the push that would not go")

        log.clear(ProblemRules.SYNC)

        assertEquals(emptyList<Any>(), log.problems.value)
        assertFalse(ProblemRules.isMarked(log.problems.value))
    }

    @Test
    fun `a kind coming right leaves the others alone`() {
        log.report(ProblemRules.SYNC, "one")
        log.report(ProblemRules.BACKUP, "two")

        log.clear(ProblemRules.BACKUP)

        assertEquals(listOf(ProblemRules.SYNC), log.problems.value.map { it.kind })
    }
}
