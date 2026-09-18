package com.goalmaker.app.application.planning

import com.goalmaker.app.contracts.ContractFiles
import com.goalmaker.app.domain.planning.Recurrence
import java.time.LocalDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs contracts/vectors/recurrence.json, which the Windows app passes too. */
class RecurrenceContractTest {
    private val vectors = ContractFiles.load("vectors/recurrence.json")

    @Test
    fun `every next day`() {
        val cases = vectors.getValue("next").jsonArray.map { it.jsonObject }
        val failures = cases.mapNotNull { case ->
            val expected = date(case.getValue("next"))
            val actual = Recurrence.parse(case.getValue("rule").jsonPrimitive.content)
                ?.next(date(case.getValue("planned")), date(case.getValue("today"))!!)
            if (actual == expected) null else "${case.getValue("name").jsonPrimitive.content}: expected $expected, got $actual"
        }
        assertTrue(failures.joinToString("\n", prefix = "${failures.size} of ${cases.size} failed:\n"), failures.isEmpty())
    }

    @Test
    fun `successor and tag link ids`() {
        vectors.getValue("successors").jsonArray.map { it.jsonObject }.forEach { pair ->
            assertEquals(pair.getValue("successor").jsonPrimitive.content, Occurrences.successorId(pair.getValue("id").jsonPrimitive.content))
        }
        vectors.getValue("tagLinks").jsonArray.map { it.jsonObject }.forEach { link ->
            assertEquals(
                link.getValue("id").jsonPrimitive.content,
                Occurrences.tagLinkId(link.getValue("task").jsonPrimitive.content, link.getValue("tag").jsonPrimitive.content),
            )
        }
    }

    @Test
    fun `every repair`() {
        vectors.getValue("repair").jsonArray.map { it.jsonObject }.forEach { case ->
            val tasks = case.getValue("occurrences").jsonArray.map { it.jsonObject }.map { occurrence ->
                TaskItem(
                    id = occurrence.getValue("id").jsonPrimitive.content,
                    title = "Run",
                    state = when (occurrence.getValue("status").jsonPrimitive.content) {
                        "done" -> TaskState.DONE
                        "dropped" -> TaskState.DROPPED
                        else -> TaskState.OPEN
                    },
                    topPriority = false,
                    createdAt = "2026-09-10T08:00:00.000000Z",
                    plannedDate = date(occurrence.getValue("planned")),
                    recurrence = "FREQ=DAILY",
                    deleted = occurrence["deleted"]?.jsonPrimitive?.boolean ?: false,
                    seriesId = occurrence.getValue("series").takeUnless { it == JsonNull }?.jsonPrimitive?.content,
                )
            }
            val expected = case.getValue("drop").jsonArray.map { it.jsonPrimitive.content }.sorted()
            assertEquals(case.getValue("name").jsonPrimitive.content, expected, Occurrences.toDrop(tasks).sorted())
        }
    }

    private fun date(value: JsonElement): LocalDate? = value.takeUnless { it == JsonNull }?.jsonPrimitive?.content?.let(LocalDate::parse)
}
