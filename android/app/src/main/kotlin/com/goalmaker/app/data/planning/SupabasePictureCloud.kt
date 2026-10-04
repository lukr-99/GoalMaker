package com.goalmaker.app.data.planning

import com.goalmaker.app.application.planning.PictureCloud
import com.goalmaker.app.application.sync.NotSignedInException
import com.goalmaker.app.application.sync.RemoteRejectedException
import com.goalmaker.app.application.sync.RemoteUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * [PictureCloud] over Supabase Storage (`/storage/v1/object/...`) with the publishable key and the
 * owner's access token, in the life-goal-pictures bucket (ADR 0018). A 401 gets one [refreshSession]
 * and one more try, like PostgREST calls; offline, a timeout or a busy server is
 * RemoteUnavailableException, anything else the server refuses RemoteRejectedException.
 */
class SupabasePictureCloud(
    private val http: HttpClient,
    baseUrl: String,
    private val publishableKey: String,
    private val refreshSession: suspend () -> Boolean = { false },
    private val accessToken: suspend () -> String?,
) : PictureCloud {
    private val objects = baseUrl.trimEnd('/') + "/storage/v1/object/"

    override suspend fun upload(owner: String, id: String, bytes: ByteArray) {
        send(HttpMethod.Post, "$BUCKET/$owner/$id.jpg") {
            header("x-upsert", "true")
            contentType(ContentType.Image.JPEG)
            setBody(bytes)
        }
    }

    override suspend fun download(owner: String, id: String): ByteArray? =
        send(HttpMethod.Get, "authenticated/$BUCKET/$owner/$id.jpg", missingIsFine = true)

    override suspend fun remove(owner: String, id: String) {
        send(HttpMethod.Delete, "$BUCKET/$owner/$id.jpg", missingIsFine = true)
    }

    private suspend fun send(
        method: HttpMethod,
        path: String,
        missingIsFine: Boolean = false,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): ByteArray? {
        var response = call(method, path, configure)
        if (response.status == HttpStatusCode.Unauthorized && refreshSession()) response = call(method, path, configure)
        val status = response.status
        if (status.isSuccess()) return response.bodyAsBytes()
        // Storage answers a missing file with 400 or 404 and "not_found" in the body.
        val text = response.bodyAsText()
        if (missingIsFine && (status == HttpStatusCode.NotFound || (status == HttpStatusCode.BadRequest && "not_found" in text.lowercase()))) {
            return null
        }
        val temporary = status == HttpStatusCode.Unauthorized || status == HttpStatusCode.RequestTimeout ||
            status == HttpStatusCode.TooManyRequests || status.value >= 500
        val message = "HTTP ${status.value}: ${text.take(300)}"
        throw if (temporary) RemoteUnavailableException(message) else RemoteRejectedException(message)
    }

    private suspend fun call(method: HttpMethod, path: String, configure: HttpRequestBuilder.() -> Unit): HttpResponse {
        val token = accessToken() ?: throw NotSignedInException("Not signed in.")
        try {
            return http.request(objects + path) {
                this.method = method
                header("apikey", publishableKey)
                bearerAuth(token)
                configure()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            throw RemoteUnavailableException("The server can't be reached.", error)
        }
    }

    private companion object {
        const val BUCKET = "life-goal-pictures"
    }
}
