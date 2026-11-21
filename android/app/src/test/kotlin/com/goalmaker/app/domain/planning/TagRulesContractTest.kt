package com.goalmaker.app.domain.planning

import com.goalmaker.app.application.planning.TagItem
import com.goalmaker.app.contracts.ContractFiles
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs contracts/vectors/tags.json, which the Windows app and the connector pass too. */
class TagRulesContractTest {
    private val vectors = ContractFiles.load("vectors/tags.json")

    private fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.content
    private fun JsonObject.optional(name: String) = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.cases(name: String) = getValue(name).jsonArray.map { it.jsonObject }
    private fun JsonObject.ids(name: String) = getValue(name).jsonArray.map { it.jsonPrimitive.content }

    private fun tags(case: JsonObject) = case.getValue("tags").jsonArray.map { element ->
        val tag = element.jsonObject
        TagItem(tag.text("id"), tag.text("id"), tag.optional("area"), tag.optional("project"))
    }

    private fun projects(case: JsonObject) = case.getValue("projects").jsonObject.mapValues { (_, area) ->
        (area as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    private fun label(place: TagPlace?) = when (place) {
        null -> null
        is TagPlace.Area -> "area:${place.id}"
        is TagPlace.Project -> "project:${place.id}"
    }

    @Test
    fun `what a tag picker offers`() {
        vectors.cases("offered").forEach { case ->
            val chosen = case.getValue("chosen").jsonObject
            val offered = TagRules.offered(
                tags(case),
                case.ids("areas").toSet(),
                projects(case),
                areaId = chosen.optional("area"),
                projectId = chosen.optional("project"),
            )
            assertEquals(case.text("name"), case.ids("expect"), offered.map(TagItem::id))
        }
    }

    @Test
    fun `how Settings groups the tags`() {
        vectors.cases("groups").forEach { case ->
            val groups = TagRules.groups(tags(case), case.ids("areas"), projects(case).keys.toList())
            val expect = case.getValue("expect").jsonArray.map { element ->
                val group = element.jsonObject
                group.optional("place") to group.ids("tags")
            }
            assertEquals(case.text("name"), expect, groups.map { label(it.place) to it.tags.map(TagItem::id) })
        }
    }
}
