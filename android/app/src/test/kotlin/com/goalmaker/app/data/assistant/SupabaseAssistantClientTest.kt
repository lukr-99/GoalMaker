package com.goalmaker.app.data.assistant

import com.goalmaker.app.application.assistant.AssistantReply
import com.goalmaker.app.application.assistant.ChatMessage
import com.goalmaker.app.application.assistant.ChatRole
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The assistant call's shape and how each answer maps (.scratch/m7-quick-chat/README.md, "The call"). */
class SupabaseAssistantClientTest {
    private val answers = ArrayDeque<Pair<HttpStatusCode, String>>()
    private val seen = mutableListOf<Triple<String, String?, String>>()
    private var unreachable = false
    private val http = HttpClient(
        MockEngine { request ->
            if (unreachable) throw IOException("no route to host")
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().toString(Charsets.UTF_8)
            seen += Triple(request.url.toString(), request.headers[HttpHeaders.Authorization], body)
            val (status, text) = answers.removeFirst()
            respond(text, status)
        },
    ) {
        install(HttpTimeout)
    }

    private var token: String? = "first"
    private var refreshes = 0
    private var refreshWorks = true

    private val client = SupabaseAssistantClient(
        http = http,
        baseUrl = "http://localhost/",
        publishableKey = "key",
        refreshSession = {
            refreshes++
            if (refreshWorks) token = "second"
            refreshWorks
        },
    ) { token }

    private val thread = listOf(
        ChatMessage(ChatRole.USER, "add call the bank tomorrow at 9"),
        ChatMessage(ChatRole.MODEL, "Added it."),
        ChatMessage(ChatRole.USER, "what's on today?"),
    )

    @Test
    fun `the whole thread goes to the assistant function with the owner's token`() = runTest {
        answers += HttpStatusCode.OK to """{"text":"Two tasks."}"""

        assertEquals(AssistantReply.Answer("Two tasks."), client.ask(thread))

        val (url, auth, body) = seen.single()
        assertEquals("http://localhost/functions/v1/assistant", url)
        assertEquals("Bearer first", auth)
        assertEquals(
            Json.parseToJsonElement(
                """{"messages":[{"role":"user","text":"add call the bank tomorrow at 9"},""" +
                    """{"role":"model","text":"Added it."},{"role":"user","text":"what's on today?"}]}""",
            ),
            Json.parseToJsonElement(body),
        )
    }

    @Test
    fun `each error code has its reply`() = runTest {
        val cases = listOf(
            HttpStatusCode.ServiceUnavailable to "unavailable" to AssistantReply.Unavailable,
            HttpStatusCode.TooManyRequests to "rate_limited" to AssistantReply.RateLimited,
            HttpStatusCode.TooManyRequests to "provider_limit" to AssistantReply.ProviderLimit,
            HttpStatusCode.BadRequest to "bad_request" to AssistantReply.BadRequest,
            HttpStatusCode.BadGateway to "failed" to AssistantReply.Failed,
        )
        cases.forEach { (answer, expected) ->
            val (status, code) = answer
            answers += status to """{"error":"$code","message":"words"}"""
            assertEquals(code, expected, client.ask(thread))
        }
    }

    @Test
    fun `an answer without a code is read by its status`() = runTest {
        answers += HttpStatusCode.NotFound to "Function not found"
        answers += HttpStatusCode.InternalServerError to ""
        answers += HttpStatusCode.OK to """{"nothing":"here"}"""

        assertEquals(AssistantReply.Unavailable, client.ask(thread))
        assertEquals(AssistantReply.Failed, client.ask(thread))
        assertEquals(AssistantReply.Failed, client.ask(thread))
    }

    @Test
    fun `a turned away token gets one refresh, and a second no is signed out`() = runTest {
        answers += HttpStatusCode.Unauthorized to ""
        answers += HttpStatusCode.OK to """{"text":"Hi."}"""
        assertEquals(AssistantReply.Answer("Hi."), client.ask(thread))
        assertEquals(listOf("Bearer first", "Bearer second"), seen.map { it.second })

        answers += HttpStatusCode.Unauthorized to ""
        answers += HttpStatusCode.Unauthorized to ""
        assertEquals(AssistantReply.SignedOut, client.ask(thread))
        assertEquals(2, refreshes)
    }

    @Test
    fun `a refresh that could not happen is offline`() = runTest {
        refreshWorks = false
        answers += HttpStatusCode.Unauthorized to ""

        assertEquals(AssistantReply.Offline, client.ask(thread))
    }

    @Test
    fun `no session never calls, and no connection is offline`() = runTest {
        token = null
        assertEquals(AssistantReply.SignedOut, client.ask(thread))
        assertTrue(seen.isEmpty())

        token = "first"
        unreachable = true
        assertEquals(AssistantReply.Offline, client.ask(thread))
    }
}
