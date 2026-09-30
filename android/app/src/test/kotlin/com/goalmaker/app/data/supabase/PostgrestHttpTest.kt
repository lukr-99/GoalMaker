package com.goalmaker.app.data.supabase

import com.goalmaker.app.application.sync.NotSignedInException
import com.goalmaker.app.application.sync.RemoteUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

/** A token the server turns away gets one refresh; turned away again, the session ends (docs/sign-in.md). */
class PostgrestHttpTest {
    // The server's answers in order, and the tokens it was shown.
    private val answers = ArrayDeque<HttpStatusCode>()
    private val tokens = mutableListOf<String?>()
    private val http = HttpClient(
        MockEngine { request ->
            tokens += request.headers[HttpHeaders.Authorization]
            respond("[]", answers.removeFirst())
        },
    )

    // The fake session: a token, a refresh that hands out the next one, and whether it ended.
    private var token: String? = "first"
    private var refreshes = 0
    private var refreshWorks = true
    private var ended = false

    private val postgrest = PostgrestHttp(
        http = http,
        baseUrl = "http://localhost",
        publishableKey = "key",
        refreshSession = {
            refreshes++
            if (refreshWorks) token = "second"
            refreshWorks
        },
        endSession = {
            ended = true
            token = null
        },
    ) { token }

    @Test
    fun `a refresh that brings a new token tries once more`() = runTest {
        answers += listOf(HttpStatusCode.Unauthorized, HttpStatusCode.OK)

        assertEquals("[]", postgrest.send(HttpMethod.Get, "tasks"))

        assertEquals(listOf("Bearer first", "Bearer second"), tokens)
        assertEquals(1, refreshes)
        assertFalse(ended)
    }

    @Test
    fun `turned away after a refresh, the session ends`() = runTest {
        answers += listOf(HttpStatusCode.Unauthorized, HttpStatusCode.Unauthorized)

        try {
            postgrest.send(HttpMethod.Get, "tasks")
            fail("expected the session to end")
        } catch (expected: NotSignedInException) {
            assertEquals(true, ended)
        }

        // Nobody signed in now: the next call does not reach the server.
        try {
            postgrest.send(HttpMethod.Get, "tasks")
            fail("expected no session")
        } catch (expected: NotSignedInException) {
            assertEquals(2, tokens.size)
        }
    }

    @Test
    fun `a refresh that could not happen is offline, not the end`() = runTest {
        refreshWorks = false
        answers += HttpStatusCode.Unauthorized

        try {
            postgrest.send(HttpMethod.Get, "tasks")
            fail("expected offline")
        } catch (expected: RemoteUnavailableException) {
            assertFalse(expected is NotSignedInException)
        }

        assertEquals(1, refreshes)
        assertFalse(ended)
    }
}
