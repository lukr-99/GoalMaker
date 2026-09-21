package com.goalmaker.app.data.diagnostics

import java.io.File
import java.time.Instant

/**
 * Writes an unhandled exception to `files/crash.log` before the process goes, so a crash on the
 * owner's phone leaves something to read (`android/tools/pull-debug-files.ps1 -FilePattern '\.log$'`).
 * It is the phone's side of the Windows app's crash log and holds nothing personal beyond what an
 * exception message carries. Whatever handler Android had is still called afterwards.
 */
class CrashLog(private val file: File, private val next: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, error: Throwable) {
        runCatching { write(thread, error) }
        next?.uncaughtException(thread, error)
    }

    private fun write(thread: Thread, error: Throwable) {
        file.parentFile?.mkdirs()
        // Keep the newest crashes, never a year of them.
        if (file.length() > MAX_BYTES) file.writeText("")
        file.appendText("${Instant.now()} ${thread.name}\n${error.stackTraceToString()}\n")
    }

    companion object {
        private const val MAX_BYTES = 64L * 1024

        /** Writes one throwable, for a failure caught before the process goes. */
        fun write(folder: File, error: Throwable) {
            runCatching { CrashLog(File(folder, "crash.log"), null).uncaughtException(Thread.currentThread(), error) }
        }

        /** Starts logging into [folder], keeping the handler Android already had. */
        fun install(folder: File) {
            val existing = Thread.getDefaultUncaughtExceptionHandler()
            if (existing is CrashLog) return
            Thread.setDefaultUncaughtExceptionHandler(CrashLog(File(folder, "crash.log"), existing))
        }
    }
}
