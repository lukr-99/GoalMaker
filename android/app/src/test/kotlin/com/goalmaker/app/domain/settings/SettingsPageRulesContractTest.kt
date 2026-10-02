package com.goalmaker.app.domain.settings

import com.goalmaker.app.contracts.ContractFiles
import com.goalmaker.app.application.update.UpdateRequest
import com.goalmaker.app.ui.settings.SettingsSection
import com.goalmaker.app.ui.settings.SettingsSections
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Runs contracts/vectors/settings.json, which the Windows app passes too. */
class SettingsPageRulesContractTest {
    private val vectors = ContractFiles.load("vectors/settings.json")

    private fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.content
    private fun JsonObject.cases(name: String) = getValue(name).jsonArray.map { it.jsonObject }
    private fun JsonObject.number(name: String) = getValue(name).jsonPrimitive.int
    private fun JsonObject.flag(name: String) = getValue(name).jsonPrimitive.boolean
    private fun JsonElement.ids() = jsonArray.map { it.jsonPrimitive.content }
    private fun JsonObject.idOrNull(name: String) = getValue(name).let { if (it is JsonNull) null else it.jsonPrimitive.content }

    @Test
    fun `the phone's sections are the contract's, in its order`() {
        assertEquals(vectors.getValue("order").ids(), SettingsSection.entries.map { it.id })
    }

    @Test
    fun `the navigation numbers`() {
        val navigation = vectors.getValue("navigation").jsonObject
        assertEquals(navigation.number("minimumSections"), SettingsPageRules.MINIMUM_SECTIONS)
        assertEquals(navigation.number("currentLine"), SettingsPageRules.CURRENT_LINE)
        assertEquals(navigation.number("bottomSlack"), SettingsPageRules.BOTTOM_SLACK)
        assertEquals(vectors.number("scrollHintIdle").toLong(), SettingsPageRules.SCROLL_HINT_IDLE_MILLIS)
    }

    @Test
    fun `every showNavigation case`() {
        vectors.cases("showNavigation").forEach { case ->
            assertEquals(case.text("name"), case.flag("expect"), SettingsPageRules.showNavigation(case.getValue("sections").ids().size))
        }
    }

    @Test
    fun `every current case`() {
        vectors.cases("current").forEach { case ->
            val tops = case.getValue("tops").jsonObject.mapValues { it.value.jsonPrimitive.int }
            val actual = SettingsPageRules.current(
                sections = case.getValue("sections").ids(),
                tops = tops,
                scroll = case.number("scroll"),
                maxScroll = case.number("maxScroll"),
                pinned = case.idOrNull("pinned"),
            )
            assertEquals(case.text("name"), case.idOrNull("expect"), actual)
        }
    }

    @Test
    fun `every jumpScroll case`() {
        vectors.cases("jumpScroll").forEach { case ->
            assertEquals(
                case.text("name"),
                case.number("expect"),
                SettingsPageRules.jumpScrollMillis(case.number("distance"), case.flag("reduceMotion")),
            )
        }
    }

    @Test
    fun `every hint case`() {
        vectors.cases("hints").forEach { case ->
            val kind = when (case.text("kind")) {
                "jump" -> HintKind.JUMP
                "scroll" -> HintKind.SCROLL
                else -> error("unknown hint kind")
            }
            val actual = SettingsPageRules.hint(kind, case.flag("reduceMotion"))
            val expect = case.getValue("expect")
            if (expect is JsonNull) {
                assertNull(case.text("name"), actual)
                return@forEach
            }
            val e = expect.jsonObject
            val wanted = SectionHint(
                riseMillis = e.number("rise"),
                holdMillis = e.number("hold"),
                fadeMillis = e.number("fade"),
                tint = e.flag("tint"),
                ring = e.flag("ring"),
                glow = e.flag("glow"),
                edge = e.getValue("edge").jsonPrimitive.float,
                title = e.flag("title"),
            )
            assertEquals(case.text("name"), wanted, actual)
        }
    }

    @Test
    fun `every scrollHints case`() {
        vectors.cases("scrollHints").forEach { case ->
            val actual = SettingsPageRules.playsScrollHint(
                current = case.idOrNull("current"),
                lastHinted = case.idOrNull("lastHinted"),
                jumping = case.flag("jumping"),
                reduceMotion = case.flag("reduceMotion"),
            )
            assertEquals(case.text("name"), case.flag("expect"), actual)
        }
    }

    @Test
    fun `the saved mark's timing`() {
        val saved = vectors.getValue("saved").jsonObject
        assertEquals(saved.number("in"), SettingsPageRules.SAVED_IN_MILLIS)
        assertEquals(saved.number("hold").toLong(), SettingsPageRules.SAVED_HOLD_MILLIS)
        assertEquals(saved.number("out"), SettingsPageRules.SAVED_OUT_MILLIS)
    }

    @Test
    fun `every deep link lands where the contract says`() {
        vectors.cases("deepLinks").forEach { case ->
            val landing = when (case.text("link")) {
                "update" -> SettingsSections.target(UpdateRequest.SHOW)
                else -> error("unknown link")
            }
            assertEquals(case.text("name"), case.text("expect"), landing?.id)
        }
    }
}
