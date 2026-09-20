package com.goalmaker.app.application.backup

import android.app.Application
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.domain.backup.BackupProblem
import com.goalmaker.app.domain.backup.BackupRules
import com.goalmaker.app.domain.composer.ComposerParser
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Export and restore against a real replica (M6-02): what a file carries and what it does coming back. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BackupServiceTest {
    private val day = LocalDate.parse("2026-09-18")
    private lateinit var test: TestReplica
    private lateinit var tasks: TaskList
    private lateinit var backup: BackupService

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-18T12:00:00Z") })
        val areas = AreaList(test.replica, rows, listOf("violet", "blue"), {})
        tasks = TaskList(test.replica, rows, areas, TagList(test.replica, rows, {}), ProjectList(test.replica, rows, {}), {}) { day }
        backup = BackupService(
            catalog = test.catalog,
            replica = test.replica,
            ownerId = { TestReplica.OWNER },
            appVersion = "1.0.0",
            app = "android",
            now = { Instant.parse("2026-09-18T12:30:00Z") },
        )
    }

    @After
    fun tearDown() = test.close()

    private fun add(line: String) = tasks.add(ComposerParser.parse(line, LocalDateTime.parse("2026-09-18T09:00")))!!

    @Test
    fun `an export carries the owner's rows and leaves tombstones out`() {
        val kept = add("Call the bank @Health")
        val gone = add("Old thing")
        tasks.delete(gone.id)

        val text = backup.export()!!
        assertTrue(text.startsWith("{\n  \"format\": \"goalmaker.backup\""))
        assertTrue(text.contains("\"appVersion\": \"1.0.0\""))
        assertTrue(text.contains(kept.id))
        assertFalse("a deleted row is not carried", text.contains(gone.id))
        assertTrue("its area comes too", text.contains("\"areas\": ["))
    }

    @Test
    fun `a file restores into an empty replica row for row`() {
        add("Call the bank 17:00 @Health #errand")
        add("Water the plants")
        val text = backup.export()!!
        val before = test.catalog.tables.associate { it.name to test.replica.all(it.name).size }

        test.replica.clearAll()
        assertEquals(0, test.replica.all("tasks").size)

        val report = backup.restore(text)!!
        assertEquals(0, report.kept)
        assertEquals(0, report.updated)
        assertEquals(before.values.sum(), report.added)
        assertEquals(before, test.catalog.tables.associate { it.name to test.replica.all(it.name).size })
        assertEquals("the restored rows wait to be pushed", report.added, test.replica.outbox().size)
    }

    @Test
    fun `a restore never undoes newer work, and never deletes what the file lacks`() {
        val task = add("Call the bank")
        val text = backup.export()!!

        tasks.rename(task.id, "Call the bank about the fee")
        stamp(task.id, "2026-09-19T08:00:00.000000Z")
        val later = add("Made after the export")

        val report = backup.restore(text)!!

        assertEquals("the file's older row is kept out", 0, report.updated)
        assertTrue(report.kept > 0)
        assertEquals("Call the bank about the fee", tasks.find(task.id)!!.title)
        assertNotNull("a row the file lacks stays", tasks.find(later.id))
    }

    @Test
    fun `a file that is newer wins`() {
        val task = add("Call the bank")
        stamp(task.id, "2026-09-20T08:00:00.000000Z")
        val text = backup.export()!!
        tasks.rename(task.id, "Older title")
        stamp(task.id, "2026-09-19T08:00:00.000000Z")

        val report = backup.restore(text)!!

        assertEquals(1, report.updated)
        assertEquals("Call the bank", tasks.find(task.id)!!.title)
    }

    @Test
    fun `a preview says what a restore would do and changes nothing`() {
        add("Call the bank")
        val text = backup.export()!!
        test.replica.clearAll()

        val preview = backup.preview(text)!!

        assertTrue(preview.added > 0)
        assertEquals(0, test.replica.all("tasks").size)
        assertTrue(test.replica.outbox().isEmpty())
    }

    @Test
    fun `a refused file writes nothing and says why`() {
        add("Call the bank")
        val good = backup.export()!!

        for ((text, problem) in listOf(
            "not json at all" to BackupProblem.NOT_A_BACKUP,
            good.replace("\"version\": 1", "\"version\": 99") to BackupProblem.TOO_NEW,
            good.replace(TestReplica.OWNER, "22222222-2222-4222-8222-222222222222") to BackupProblem.ANOTHER_OWNER,
            good.replace("\"areas\": [", "\"sprints\": [") to BackupProblem.UNKNOWN_TABLE,
        )) {
            test.replica.clearAll()
            assertEquals(problem, backup.check(text))
            assertNull(backup.restore(text))
            assertEquals("nothing was written", 0, test.replica.all("tasks").size)
        }
    }

    @Test
    fun `the file is named after the day and reads back as the same document`() {
        add("Call the bank")
        val text = backup.export()!!
        val document = BackupRules.read(kotlinx.serialization.json.Json.parseToJsonElement(text))!!

        assertEquals("goalmaker-2026-09-18.json", BackupRules.fileName("2026-09-18"))
        assertEquals("android", document.app)
        assertEquals(backup.read()!!.rowCount, document.rowCount)
    }

    // A server timestamp on a row, the way a pull would leave it.
    private fun stamp(id: String, updatedAt: String) {
        val row = test.replica.get("tasks", id)!!
        test.replica.put("tasks", JsonObject(row + ("updated_at" to kotlinx.serialization.json.JsonPrimitive(updatedAt))))
    }
}
