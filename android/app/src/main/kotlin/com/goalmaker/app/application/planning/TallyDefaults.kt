package com.goalmaker.app.application.planning

import java.io.InputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The Tally categories and rules the app ships (contracts/content/tally-rules.json), in the file's
 * order: the rules come after the owner's own, and the first match wins (docs/tally.md).
 */
class TallyDefaults(val categories: List<TallyCategory>, val rules: List<TallyRule>) {
    companion object {
        fun parse(text: String): TallyDefaults {
            val document = Json.parseToJsonElement(text).jsonObject
            return TallyDefaults(
                categories = document.getValue("categories").jsonArray.mapIndexed { index, element ->
                    val category = element.jsonObject
                    TallyCategory(
                        id = category.getValue("id").jsonPrimitive.content,
                        name = category.getValue("name").jsonPrimitive.content,
                        color = category.getValue("color").jsonPrimitive.content,
                        emoji = category.text("emoji"),
                        position = index,
                    )
                },
                rules = document.getValue("rules").jsonArray.map { parseRule(it.jsonObject) },
            )
        }

        fun load(stream: InputStream): TallyDefaults = stream.use { parse(it.readBytes().toString(Charsets.UTF_8)) }

        /** One rule as the shipped file and contracts/vectors/tally.json write it. */
        fun parseRule(rule: JsonObject) = TallyRule(
            match = rule.getValue("match").jsonPrimitive.content,
            pattern = rule.getValue("pattern").jsonPrimitive.content,
            platform = rule.getValue("platform").jsonPrimitive.content,
            category = rule.getValue("category").jsonPrimitive.content,
            project = rule.text("project"),
        )

        private fun JsonObject.text(name: String): String? = this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
    }
}
