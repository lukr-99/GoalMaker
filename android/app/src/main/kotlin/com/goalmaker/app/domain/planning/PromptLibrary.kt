package com.goalmaker.app.domain.planning

import java.io.InputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The review prompts the app ships (contracts/content/prompts.json), in the file's order: the
 * rotation walks the [categories] and takes from [prompts] (docs/reviews.md).
 */
class PromptLibrary(val version: Int, val categories: List<String>, val prompts: List<ReviewPrompt>) {
    private val byId = prompts.associateBy(ReviewPrompt::id)

    operator fun get(id: String): ReviewPrompt? = byId[id]

    companion object {
        fun parse(text: String): PromptLibrary {
            val document = Json.parseToJsonElement(text).jsonObject
            return PromptLibrary(
                version = document.getValue("version").jsonPrimitive.content.toInt(),
                categories = document.getValue("categories").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content },
                prompts = document.getValue("prompts").jsonArray.map { element ->
                    val prompt = element.jsonObject
                    ReviewPrompt(
                        id = prompt.getValue("id").jsonPrimitive.content,
                        category = prompt.getValue("category").jsonPrimitive.content,
                        reviews = prompt.getValue("reviews").jsonArray.map { it.jsonPrimitive.content }.toSet(),
                        text = prompt.getValue("text").jsonPrimitive.content,
                        trigger = prompt.text("trigger"),
                    )
                },
            )
        }

        fun load(stream: InputStream): PromptLibrary = stream.use { parse(it.readBytes().toString(Charsets.UTF_8)) }

        private fun JsonObject.text(name: String): String? =
            this[name]?.jsonPrimitive?.takeIf { it.booleanOrNull == null && it.content != "null" }?.content
    }
}
