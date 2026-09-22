package com.goalmaker.app.data.update

import com.goalmaker.app.domain.update.ReleaseChannelAddress
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GitHubReleaseChannelTest {
    private val requested = mutableListOf<String>()
    private val files = mutableMapOf<String, ByteArray>()
    private val http = HttpClient(
        MockEngine { request ->
            val url = request.url.toString()
            requested += url
            val body = files[url]
            if (body == null) respond("", HttpStatusCode.NotFound) else respond(body, HttpStatusCode.OK)
        },
    )
    private val directory: File = Files.createTempDirectory("updates").toFile()
    private val channel = GitHubReleaseChannel(http, ReleaseChannelAddress("https://github.com/owner/app"), directory)

    @Test
    fun `the latest manifest and its signature come from the latest release`() = runTest {
        val manifest = "{\"schema\":1}\n".toByteArray()
        files["https://github.com/owner/app/releases/latest/download/manifest.json"] = manifest
        files["https://github.com/owner/app/releases/latest/download/manifest.sig"] = "c2ln\n".toByteArray()

        val snapshot = channel.fetchLatest()

        assertArrayEquals(manifest, snapshot.manifestBytes)
        assertEquals("c2ln", snapshot.signatureBase64)
    }

    @Test
    fun `a missing manifest is an error, not an empty channel`() = runTest {
        try {
            channel.fetchLatest()
            fail("expected the missing manifest to throw")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("404"))
        }
    }

    @Test
    fun `an artifact is streamed to the updates folder with its size and hash`() = runTest {
        val apk = ByteArray(200_000) { (it % 251).toByte() }
        files["https://github.com/owner/app/releases/download/v1.2.0/GoalMaker-1.2.0.apk"] = apk
        File(directory, "old.apk").writeText("left over")
        var progress = 0L

        val downloaded = channel.download("1.2.0/GoalMaker-1.2.0.apk") { progress = it }

        assertEquals(File(directory, "GoalMaker-1.2.0.apk").absolutePath, downloaded.localPath)
        assertArrayEquals(apk, File(downloaded.localPath).readBytes())
        assertEquals(apk.size.toLong(), downloaded.size)
        assertEquals(apk.size.toLong(), progress)
        val expected = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
        assertEquals(expected, downloaded.sha256)
        assertEquals(listOf("GoalMaker-1.2.0.apk"), directory.list()!!.toList())
    }

    @Test
    fun `a path outside the release layout is refused without a request`() = runTest {
        try {
            channel.download("../1.2.0/GoalMaker-1.2.0.apk") {}
            fail("expected the path to be refused")
        } catch (expected: IOException) {
            assertTrue(requested.isEmpty())
        }
    }
}
