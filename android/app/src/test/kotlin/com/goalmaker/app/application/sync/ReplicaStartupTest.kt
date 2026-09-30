package com.goalmaker.app.application.sync

import android.app.Application
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.goalmaker.app.data.diagnostics.CrashLog
import com.goalmaker.app.data.replica.ReplicaMigrator
import com.goalmaker.app.data.replica.SqliteReplica
import com.goalmaker.app.data.replica.TestReplica
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** A replica that will not open is a state the app can show and a line in the crash log, never a throw (M6-06). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReplicaStartupTest {
    private lateinit var test: TestReplica
    private val logs: File = Files.createTempDirectory("crash-log").toFile()
    private val crashLog get() = File(logs, "crash.log")

    @Before
    fun setUp() {
        test = TestReplica()
    }

    @After
    fun tearDown() {
        test.close()
        logs.deleteRecursively()
    }

    private fun startup(replica: Replica) = ReplicaStartup(replica) { CrashLog.write(logs, it) }

    // The same file, opened by an app that has fewer migrations than the one that wrote it.
    private fun olderApp(): SqliteReplica {
        val assets = RuntimeEnvironment.getApplication().assets
        return SqliteReplica(AndroidSQLiteDriver(), test.file.path, test.catalog) {
            ReplicaMigrator.builtIn(assets).dropLast(1)
        }
    }

    @Test
    fun `a replica that opens is open and logs nothing`() {
        val startup = startup(test.replica)

        assertEquals(ReplicaOpening.Opening, startup.state.value)
        assertEquals(ReplicaOpening.Open, startup.open())
        assertEquals(ReplicaOpening.Open, startup.state.value)
        assertTrue(!crashLog.exists())
    }

    @Test
    fun `a replica a newer app wrote is a failure that says to update`() {
        test.replica.open()
        test.replica.close()

        olderApp().use { older ->
            val startup = startup(older)

            assertEquals(ReplicaOpening.Failed(newerApp = true), startup.open())
            assertEquals(ReplicaOpening.Failed(newerApp = true), startup.state.value)
        }
        val written = crashLog.readText()
        assertTrue(written, written.contains("ReplicaFromNewerAppException"))
        assertTrue(written, written.contains("which this app doesn't know"))
    }

    @Test
    fun `a replica that will not open is a failure and a line in the crash log`() {
        // What the bundled driver says about a corrupt file. The framework driver the tests run on
        // quietly starts a new one instead, so the failure is played rather than made.
        val broken = object : Replica by test.replica {
            override fun open() = throw IllegalStateException("file is not a database")
        }
        val startup = startup(broken)

        assertEquals(ReplicaOpening.Failed(newerApp = false), startup.open())
        assertEquals(ReplicaOpening.Failed(newerApp = false), startup.state.value)
        val written = crashLog.readText()
        assertTrue(written, written.contains("file is not a database"))
    }
}
