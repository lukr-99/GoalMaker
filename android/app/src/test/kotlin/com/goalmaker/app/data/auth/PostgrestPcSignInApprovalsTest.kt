package com.goalmaker.app.data.auth

import com.goalmaker.app.application.auth.PcSignInAnswer
import com.goalmaker.app.application.auth.PcSignInRequest
import com.goalmaker.app.application.sync.RemoteRejectedException
import com.goalmaker.app.data.supabase.PostgrestHttp
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/** The phone's two calls for a PC's QR sign-in, over a fake PostgREST (ADR 0020, 0028). */
class PostgrestPcSignInApprovalsTest {
    private val seen = mutableListOf<Pair<String, String>>()
    private var answer = "[]"
    private val approvals = PostgrestPcSignInApprovals(
        PostgrestHttp(
            http = HttpClient(
                MockEngine { request ->
                    seen += request.url.encodedPath to (request.body as TextContent).text
                    respond(answer, HttpStatusCode.OK)
                },
            ),
            baseUrl = "http://localhost",
            publishableKey = "key",
        ) { "token" },
    )

    @Test
    fun `looking up a code reads which PC asked`() = runTest {
        answer = """[{"device":"DESKTOP-1","created_at":"2026-10-15T09:00:00+00:00","expires_at":"2026-10-15T09:02:00+00:00","choices":[17,42,88]}]"""

        val request = approvals.look("secret")

        assertEquals(
            PcSignInRequest(
                "DESKTOP-1",
                Instant.parse("2026-10-15T09:00:00Z"),
                Instant.parse("2026-10-15T09:02:00Z"),
                listOf(17, 42, 88),
            ),
            request,
        )
        assertEquals("/rest/v1/rpc/pc_sign_in_look", seen.single().first)
        assertEquals(Json.parseToJsonElement("""{"scan_secret":"secret"}"""), Json.parseToJsonElement(seen.single().second))
    }

    @Test
    fun `a code the server does not know is no request`() = runTest {
        answer = "[]"
        assertNull(approvals.look("secret"))
    }

    @Test
    fun `the answer goes with the secret and the picked number`() = runTest {
        answer = "\"approved\""
        assertEquals(PcSignInAnswer.APPROVED, approvals.answer("secret", approve = true, picked = 42))
        assertEquals("/rest/v1/rpc/pc_sign_in_answer", seen.last().first)
        assertEquals(
            Json.parseToJsonElement("""{"scan_secret":"secret","approve":true,"picked":42}"""),
            Json.parseToJsonElement(seen.last().second),
        )
    }

    @Test
    fun `every answer of the server has its own meaning`() = runTest {
        mapOf(
            "declined" to PcSignInAnswer.DECLINED,
            "mismatch" to PcSignInAnswer.MISMATCH,
            "gone" to PcSignInAnswer.GONE,
            "no_email" to PcSignInAnswer.NO_EMAIL,
        ).forEach { (said, meant) ->
            answer = "\"$said\""
            assertEquals(said, meant, approvals.answer("secret", approve = false))
        }
    }

    @Test
    fun `an answer the app doesn't know is refused`() = runTest {
        answer = "true"
        try {
            approvals.answer("secret", approve = true, picked = 42)
            fail("expected a refusal")
        } catch (_: RemoteRejectedException) {
        }
    }
}
