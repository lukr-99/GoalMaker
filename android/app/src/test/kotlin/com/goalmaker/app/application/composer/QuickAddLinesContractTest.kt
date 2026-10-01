package com.goalmaker.app.application.composer

import com.goalmaker.app.contracts.ContractFiles
import java.time.LocalDate
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs contracts/vectors/quick-add.json, which the connector's rules and the Windows app pass too. */
class QuickAddLinesContractTest {
    private val vectors = ContractFiles.load("vectors/quick-add.json")
    private val today = LocalDate.parse(vectors.getValue("today").jsonPrimitive.content)

    @Test
    fun `every want vector`() = check("wants") { line ->
        val want = QuickAddLines.readWant(line)
        mapOf(
            "title" to want.title,
            "reason" to want.reason,
            "price" to want.price,
            "currency" to want.currency,
            "waitDays" to want.waitDays?.toDouble(),
        )
    }

    @Test
    fun `every habit vector`() = check("habits") { line ->
        val habit = QuickAddLines.readHabit(line)
        mapOf(
            "name" to habit.name,
            "cadence" to habit.cadence,
            "weekdays" to habit.weekdays?.toDouble(),
            "times" to habit.times?.toDouble(),
            "measure" to habit.measure,
            "target" to habit.target,
            "unit" to habit.unit,
        )
    }

    @Test
    fun `every goal vector`() = check("goals") { line ->
        val goal = QuickAddLines.readGoal(line, today)
        mapOf(
            "title" to goal.title,
            "horizon" to goal.horizon.id,
            "periodStart" to goal.periodStart.toString(),
            "mode" to goal.mode,
            "target" to goal.target,
            "unit" to goal.unit,
        )
    }

    private fun check(group: String, read: (String) -> Map<String, Any?>) {
        val vectors = vectors.getValue(group).jsonObject
        val defaults = vectors.getValue("defaults").jsonObject
        val cases = vectors.getValue("cases").jsonArray.map { it.jsonObject }
        assertTrue("$group has no cases", cases.isNotEmpty())
        val failures = cases.mapNotNull { case ->
            val name = case.getValue("name").jsonPrimitive.content
            val line = case.getValue("line").jsonPrimitive.content
            val expected = JsonObject(defaults + case.getValue("expect").jsonObject).mapValues { (_, value) -> plain(value as? JsonPrimitive) }
            val actual = read(line)
            if (actual != expected) "$name (\"$line\"): expected $expected\n    got      $actual" else null
        }
        assertTrue(failures.joinToString("\n", prefix = "${failures.size} of ${cases.size} $group failed:\n"), failures.isEmpty())
    }

    /** A JSON value as the parser gives it: numbers as doubles, strings as text, null as null. */
    private fun plain(value: JsonPrimitive?): Any? = when {
        value == null || value is JsonNull -> null
        value.isString -> value.content
        else -> value.doubleOrNull ?: value.content
    }
}
