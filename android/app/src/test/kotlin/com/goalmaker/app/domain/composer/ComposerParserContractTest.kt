package com.goalmaker.app.domain.composer

import com.goalmaker.app.contracts.ContractFiles
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs contracts/vectors/composer.json, which the Windows parser passes too. */
class ComposerParserContractTest {
    private val vectors = ContractFiles.load("vectors/composer.json")
    private val defaults = vectors.getValue("defaults").jsonObject

    @Test
    fun `every composer vector`() {
        val cases = vectors.getValue("cases").jsonArray.map { it.jsonObject }
        val failures = cases.mapNotNull { case ->
            val (input, spans) = unmark(case.getValue("marked").jsonPrimitive.content)
            val now = LocalDateTime.parse((case["now"] ?: defaults.getValue("now")).jsonPrimitive.content)
            val rollover = (case["rolloverHour"] ?: defaults.getValue("rolloverHour")).jsonPrimitive.int
            val expected = JsonObject(defaults.getValue("expect").jsonObject + case.getValue("expect").jsonObject)
            val draft = ComposerParser.parse(input, now, rollover)
            val actual = describe(draft)
            when {
                actual != expected -> "${case.name}: expected $expected\n    got      $actual"
                draft.spans != spans -> "${case.name}: expected spans $spans\n    got            ${draft.spans}"
                else -> null
            }
        }
        assertTrue(failures.joinToString("\n", prefix = "${failures.size} of ${cases.size} failed:\n"), failures.isEmpty())
    }

    @Test
    fun `the markup reader finds offsets in the plain line`() {
        val (input, spans) = unmark("Buy [[date:tmrw]] now")
        assertEquals("Buy tmrw now", input)
        assertEquals(listOf(ComposerSpan(SpanKind.DATE, 4, 8)), spans)
    }

    private val JsonObject.name get() = getValue("name").jsonPrimitive.content

    private fun describe(draft: ComposerDraft): JsonObject = JsonObject(
        mapOf(
            "title" to JsonPrimitive(draft.title),
            "plannedDate" to (draft.plannedDate?.toString()?.let(::JsonPrimitive) ?: JsonNull),
            "plannedTime" to (draft.plannedTime?.format(TIME)?.let(::JsonPrimitive) ?: JsonNull),
            "tags" to JsonArray(draft.tags.map(::JsonPrimitive)),
            "area" to text(draft.area),
            "project" to text(draft.project),
            "topPriority" to JsonPrimitive(draft.topPriority),
            "idea" to JsonPrimitive(draft.idea),
            "repeat" to text(draft.repeat),
            "command" to (
                draft.command?.let {
                    JsonObject(mapOf("name" to JsonPrimitive(it.name), "argument" to JsonPrimitive(it.argument), "known" to JsonPrimitive(it.known)))
                } ?: JsonNull
                ),
        ),
    )

    private fun text(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull

    /** "Buy [[date:tmrw]]" -> ("Buy tmrw", a DATE span over "tmrw"). */
    private fun unmark(marked: String): Pair<String, List<ComposerSpan>> {
        val input = StringBuilder()
        val spans = mutableListOf<ComposerSpan>()
        var index = 0
        while (index < marked.length) {
            if (marked.startsWith("[[", index)) {
                val close = marked.indexOf("]]", index)
                val body = marked.substring(index + 2, close)
                val kind = SpanKind.valueOf(body.substringBefore(':').uppercase(Locale.ROOT))
                val text = body.substringAfter(':')
                spans += ComposerSpan(kind, input.length, input.length + text.length)
                input.append(text)
                index = close + 2
            } else {
                input.append(marked[index])
                index++
            }
        }
        return input.toString() to spans
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
