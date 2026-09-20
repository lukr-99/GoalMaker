package com.goalmaker.app.data.diagnostics

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The crash log keeps what went wrong and still lets Android have the exception. */
class CrashLogTest {
    private val folder: File = Files.createTempDirectory("crash-log").toFile()

    @After
    fun tearDown() {
        folder.deleteRecursively()
    }

    @Test
    fun `a crash is written down and passed on`() {
        val file = File(folder, "crash.log")
        var passed: Throwable? = null
        val log = CrashLog(file) { _, error -> passed = error }
        val boom = IllegalStateException("the graph fell over")

        log.uncaughtException(Thread.currentThread(), boom)

        val written = file.readText()
        assertTrue(written, written.contains("IllegalStateException"))
        assertTrue(written, written.contains("the graph fell over"))
        assertEquals(boom, passed)
    }

    @Test
    fun `a log that grew too big starts again`() {
        val file = File(folder, "crash.log")
        file.writeText("x".repeat(70 * 1024))
        val log = CrashLog(file, next = null)

        log.uncaughtException(Thread.currentThread(), IllegalStateException("later"))

        val written = file.readText()
        assertTrue(written, written.contains("later"))
        assertTrue(written, written.length < 70 * 1024)
    }

    @Test
    fun `a folder that cannot be written to is not a second crash`() {
        val log = CrashLog(File(folder, "gone/deeper/crash.log"), next = null)
        folder.setWritable(false)

        log.uncaughtException(Thread.currentThread(), IllegalStateException("boom"))
    }
}
