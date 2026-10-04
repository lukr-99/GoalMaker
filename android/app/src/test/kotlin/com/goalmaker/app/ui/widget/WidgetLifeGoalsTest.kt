package com.goalmaker.app.ui.widget

import com.goalmaker.app.application.planning.LifeGoalItem
import com.goalmaker.app.application.planning.LifeGoalPicture
import com.goalmaker.app.application.planning.LifeGoalRules
import com.goalmaker.app.application.planning.TimeLeft
import com.goalmaker.app.application.planning.TimeLeftUnit
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which slide the Life goals widget shows (docs/life-goals.md, M9-05). */
class WidgetLifeGoalsTest {
    private val today = LocalDate.parse("2026-10-04")
    private val car = LifeGoalItem("car", "Own an Audi R8", "Proof", by = LocalDate.parse("2036-10-04"), position = 0.0)
    private val run = LifeGoalItem("run", "Run a marathon", "To know I can", position = 1.0)
    private val boat = LifeGoalItem("boat", "Sail to Greece", "The sea", status = LifeGoalRules.ACHIEVED, position = 2.0)
    private val pictures = listOf(
        LifeGoalPicture("side", "car", 1600, 900, position = 1.0),
        LifeGoalPicture("front", "car", 1600, 900, position = 0.0),
        LifeGoalPicture("gone", "car", 1600, 900, position = 2.0, deleted = true),
        LifeGoalPicture("sails", "boat", 1600, 900),
    )

    @Test
    fun `every picture of every open life goal, and a life goal without pictures once`() {
        val slides = WidgetContent.lifeGoalSlides(listOf(run, boat, car), pictures, today)

        assertEquals(listOf("car" to "front", "car" to "side", "run" to null), slides.map { it.lifeGoalId to it.pictureId })
        assertEquals(TimeLeft(TimeLeftUnit.YEARS, 10), slides.first().timeLeft)
        assertNull(slides.last().timeLeft)
    }

    @Test
    fun `the next slide every half hour, round and round`() {
        val slides = WidgetContent.lifeGoalSlides(listOf(car, run), pictures, today)
        val start = Instant.parse("2026-10-04T12:00:00Z")
        val first = WidgetContent.lifeGoalSlide(slides, start)!!
        val index = slides.indexOf(first)

        assertEquals(first, WidgetContent.lifeGoalSlide(slides, start.plusSeconds(29 * 60)))
        assertEquals(slides[(index + 1) % 3], WidgetContent.lifeGoalSlide(slides, start.plusSeconds(30 * 60)))
        assertEquals(first, WidgetContent.lifeGoalSlide(slides, start.plusSeconds(3 * 30 * 60)))
        assertNull(WidgetContent.lifeGoalSlide(emptyList(), start))
    }
}
