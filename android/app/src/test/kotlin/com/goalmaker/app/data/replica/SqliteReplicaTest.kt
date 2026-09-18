package com.goalmaker.app.data.replica

import android.app.Application
import androidx.sqlite.driver.AndroidSQLiteDriver
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SqliteReplicaTest {
    private lateinit var test: TestReplica
    private val replica get() = test.replica

    @Before
    fun setUp() {
        test = TestReplica()
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `packaged migrations and contract are the repository files byte for byte`() {
        val assets = RuntimeEnvironment.getApplication().assets
        val repository = File(System.getProperty("goalmaker.contracts")!!).parentFile!!
        val files = repository.resolve("replica/migrations").listFiles { file -> file.extension == "sql" }.orEmpty().sortedBy { it.name }
        val packaged = ReplicaMigrator.builtIn(assets)

        assertEquals(files.map { it.name }, packaged.map { it.name })
        assertEquals(files.map { sha256(it.readBytes()) }, packaged.map { it.checksum })
        assertEquals(
            repository.resolve("contracts/schemas/synced-tables.json").readText(),
            assets.open("synced-tables.json").use { it.readBytes().toString(Charsets.UTF_8) },
        )
    }

    @Test
    fun `a changed applied migration is refused`() {
        val folder = Files.createTempDirectory("goalmaker-migrations").toFile()
        val connection = AndroidSQLiteDriver().open(File(folder, "m.db").path)
        try {
            val original = ReplicaMigrator.builtIn(RuntimeEnvironment.getApplication().assets)
            ReplicaMigrator.apply(connection, original)
            ReplicaMigrator.apply(connection, original)
            val edited = original.map { it.copy(checksum = "0".repeat(64)) }
            assertThrows(IllegalStateException::class.java) { ReplicaMigrator.apply(connection, edited) }
        } finally {
            connection.close()
            folder.deleteRecursively()
        }
    }

    @Test
    fun `queueing stores the row and one outbox entry per row`() {
        val parent = test.newTask("a", "Parent")
        replica.queue("tasks", parent)
        replica.queue("tasks", test.newTask("b", "Child"))
        replica.queue("tasks", parent.with("title" to "Parent, renamed"))

        val outbox = replica.outbox()
        assertEquals(listOf("a", "b"), outbox.map { it.rowId })
        assertTrue(outbox[0].payload.contains("Parent, renamed"))
        assertEquals("Parent, renamed", replica.get("tasks", "a")!!.text("title"))
        assertEquals(2, replica.pendingCount())
    }

    @Test
    fun `rows round-trip with their kinds`() {
        replica.put("tasks", test.newTask("a", "Run").with("top_priority" to true, "position" to 2.5, "planned_date" to "2026-09-19"))

        val stored = replica.get("tasks", "a")!!
        assertTrue((stored["top_priority"] as JsonPrimitive).boolean)
        assertEquals(2.5, (stored["position"] as JsonPrimitive).double, 0.0)
        assertEquals("2026-09-19", stored.text("planned_date"))
        assertNull(stored.text("deleted_at"))
    }

    @Test
    fun `a completed push stores the server's copy`() {
        replica.queue("tasks", test.newTask("a", "Run"))
        val entry = replica.outbox().single()

        replica.completePush(entry, test.newTask("a", "Run").with("updated_at" to "2026-09-18T10:00:00.000010Z"))

        assertTrue(replica.outbox().isEmpty())
        assertEquals("2026-09-18T10:00:00.000010Z", replica.get("tasks", "a")!!.text("updated_at"))
    }

    @Test
    fun `an edit during the push is kept and pushed next`() {
        val row = test.newTask("a", "Run")
        replica.queue("tasks", row)
        val entry = replica.outbox().single()
        replica.queue("tasks", row.with("title" to "Run 5 km"))

        replica.completePush(entry, test.newTask("a", "Run").with("updated_at" to "2026-09-18T10:00:00.000010Z"))

        assertEquals("Run 5 km", replica.get("tasks", "a")!!.text("title"))
        assertEquals(1, replica.outbox().size)
    }

    @Test
    fun `clearing for a full resync keeps pending rows`() {
        replica.put("tasks", test.newTask("synced", "Old"))
        replica.queue("tasks", test.newTask("pending", "New"))
        replica.setWatermark("tasks", "2026-09-18T10:00:00.000000Z")

        replica.clearSynced("tasks")

        assertNull(replica.get("tasks", "synced"))
        assertNotNull(replica.get("tasks", "pending"))
        assertNull(replica.watermark("tasks"))
    }

    @Test
    fun `changes are announced after the commit, once`() = runTest {
        val before = replica.watch("tasks").first()

        replica.inTransaction {
            replica.put("tasks", test.newTask("a", "One"))
            replica.put("tasks", test.newTask("b", "Two"))
        }

        assertEquals(before + 1, replica.watch("tasks").first())
        assertEquals(0L, replica.watch("areas").first())
    }

    @Test
    fun `a failed transaction leaves nothing behind`() {
        assertThrows(IllegalStateException::class.java) {
            replica.inTransaction {
                replica.queue("tasks", test.newTask("a", "One"))
                error("boom")
            }
        }

        assertNull(replica.get("tasks", "a"))
        assertTrue(replica.outbox().isEmpty())
    }

    @Test
    fun `sign-out empties everything`() {
        replica.queue("tasks", test.newTask("a", "One"))
        replica.setWatermark("tasks", "2026-09-18T10:00:00.000000Z")

        replica.clearAll()

        assertTrue(replica.all("tasks").isEmpty())
        assertTrue(replica.outbox().isEmpty())
        assertNull(replica.watermark("tasks"))
    }

    @Test
    fun `unknown tables are refused instead of becoming SQL`() {
        assertThrows(NoSuchElementException::class.java) { replica.get("tasks; DROP TABLE tasks", "a") }
        assertThrows(NoSuchElementException::class.java) { replica.put("nope", JsonObject(emptyMap())) }
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
