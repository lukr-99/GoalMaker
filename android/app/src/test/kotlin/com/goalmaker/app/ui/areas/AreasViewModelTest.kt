package com.goalmaker.app.ui.areas

import android.app.Application
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.data.replica.TestReplica
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The areas and tags manager's save rules (M2-11). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AreasViewModelTest {
    private lateinit var test: TestReplica
    private lateinit var areas: AreaList
    private lateinit var tags: TagList
    private lateinit var viewModel: AreasViewModel

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-18T12:00:00Z") })
        areas = AreaList(test.replica, rows, listOf("violet", "blue", "cyan"), {})
        tags = TagList(test.replica, rows, {})
        viewModel = AreasViewModel(areas, tags, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `a new area takes the color and emoji it was given`() = runTest {
        assertTrue(viewModel.saveArea(null, "Health", "🏃", "cyan"))

        assertEquals(listOf("Health" to "cyan"), areas.all().map { it.name to it.colorId })
        assertEquals("🏃", areas.all().single().emoji)
    }

    @Test
    fun `a new area can't take a name that is already used`() = runTest {
        viewModel.saveArea(null, "Health", "", "violet")

        assertFalse(viewModel.saveArea(null, "health", "", "blue"))
        assertFalse(viewModel.saveArea(null, "  ", "", "blue"))
        assertEquals(listOf("Health"), areas.all().map(AreaItem::name))
    }

    @Test
    fun `editing an area keeps its place and changes what was edited`() = runTest {
        viewModel.saveArea(null, "Health", "", "violet")
        viewModel.saveArea(null, "Work", "", "blue")
        val health = areas.find("Health")!!

        assertTrue(viewModel.saveArea(health.id, "Fitness", "💪", "cyan"))

        assertEquals(listOf("Fitness", "Work"), areas.all().map(AreaItem::name))
        assertEquals(AreaItem(health.id, "Fitness", "cyan", "💪"), areas.all().first())
    }

    @Test
    fun `a tag is added once, and renaming onto another tag is refused`() = runTest {
        assertTrue(viewModel.saveTag(null, "errand"))
        assertFalse(viewModel.saveTag(null, "Errand"))
        assertTrue(viewModel.saveTag(null, "call"))
        val call = tags.all().first { it.name == "call" }

        assertFalse(viewModel.saveTag(call.id, "ERRAND"))
        assertEquals(listOf("errand", "call"), tags.all().map { it.name })
    }
}
