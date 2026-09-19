package com.goalmaker.app.application.planning

import android.app.Application
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.domain.composer.ComposerParser
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Managing areas and tags on a real replica (M2-11). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AreasAndTagsTest {
    private lateinit var test: TestReplica
    private lateinit var areas: AreaList
    private lateinit var tags: TagList
    private lateinit var tasks: TaskList

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-18T12:00:00Z") })
        areas = AreaList(test.replica, rows, listOf("violet", "blue", "cyan"), {})
        tags = TagList(test.replica, rows, {})
        tasks = TaskList(test.replica, rows, areas, tags, {}) { LocalDate.parse("2026-09-18") }
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `an area is renamed, recolored and given an emoji`() {
        val home = areas.create("Home")!!

        assertTrue(areas.rename(home.id, " House "))
        assertTrue(areas.recolor(home.id, "cyan"))
        assertTrue(areas.setEmoji(home.id, "🏠"))

        assertEquals(AreaItem(home.id, "House", "cyan", "🏠"), areas.all().single())
        assertTrue(areas.setEmoji(home.id, "  "))
        assertNull(areas.all().single().emoji)
    }

    @Test
    fun `an area can't take another area's name or a color outside the palette`() {
        val home = areas.create("Home")!!
        areas.create("Work")

        assertFalse(areas.rename(home.id, "work"))
        assertFalse(areas.rename(home.id, "  "))
        assertFalse(areas.recolor(home.id, "chartreuse"))
        assertEquals(listOf("Home", "Work"), areas.all().map(AreaItem::name))
    }

    @Test
    fun `moving an area reorders them all`() {
        val home = areas.create("Home")!!
        areas.create("Work")
        areas.create("Health")

        areas.move(home.id, 2)

        assertEquals(listOf("Work", "Health", "Home"), areas.all().map(AreaItem::name))
    }

    @Test
    fun `an archived area keeps its tasks, leaves the pickers and comes back when named again`() {
        tasks.add(ComposerParser.parse("Fix the shelf @Home", LocalDateTime.parse("2026-09-18T14:00")))
        val home = areas.find("Home")!!
        areas.create("Work")

        assertTrue(areas.archive(home.id))

        assertEquals(listOf("Work"), areas.active().map(AreaItem::name))
        assertTrue(areas.find("Home")!!.archived)
        assertEquals(home.id, tasks.all().single().areaId)

        tasks.add(ComposerParser.parse("Paint the door @home", LocalDateTime.parse("2026-09-18T14:00")))

        assertEquals(listOf("Home", "Work"), areas.active().map(AreaItem::name))
        assertEquals(setOf(home.id), tasks.all().map { it.areaId }.toSet())
    }

    @Test
    fun `moving skips archived areas, which keep their place after the rest`() {
        val home = areas.create("Home")!!
        val work = areas.create("Work")!!
        areas.create("Health")
        areas.archive(work.id)

        areas.move(home.id, 1)

        assertEquals(listOf("Health", "Home", "Work"), areas.all().map(AreaItem::name))
        assertTrue(areas.restore(work.id))
        assertEquals(listOf("Health", "Home", "Work"), areas.active().map(AreaItem::name))
    }

    @Test
    fun `deleting an area keeps its tasks without it`() {
        tasks.add(ComposerParser.parse("Fix the shelf @Home", LocalDateTime.parse("2026-09-18T14:00")))
        val home = areas.find("Home")!!

        areas.delete(home.id)

        assertEquals(emptyList<AreaItem>(), areas.all())
        assertNull(tasks.all().single().areaId)
    }

    @Test
    fun `tags are renamed and deleted with their links, and the filter reads the links`() {
        tasks.add(ComposerParser.parse("Buy stamps #errand #post", LocalDateTime.parse("2026-09-18T14:00")))
        val task = tasks.all().single()
        val errand = tags.all().first { it.name == "errand" }
        val post = tags.all().first { it.name == "post" }

        assertEquals(mapOf(task.id to setOf(errand.id, post.id)), tags.links())
        assertFalse(tags.rename(errand.id, "POST"))
        assertTrue(tags.rename(errand.id, "chores"))
        tags.delete(post.id)

        assertEquals(listOf("chores"), tags.all().map(TagItem::name))
        assertEquals(mapOf(task.id to setOf(errand.id)), tags.links())
        assertEquals(listOf(task.id), ListFilter(tagId = errand.id).apply(tasks.all(), tags.links()).map(TaskItem::id))
    }
}
