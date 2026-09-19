package com.goalmaker.app.domain.design

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Everything contracts/design/themes.json defines, as the phone uses it (ADR 0008). The file is
 * checked by tools/check_design_tokens.py, so parsing trusts its shape and fails loudly otherwise.
 */
class DesignTokens(
    val defaultThemeId: String,
    val themes: List<ThemeDefinition>,
    val areaColors: List<AreaColor>,
    val density: Density,
    val motion: MotionTokens,
) {
    private val byId = themes.associateBy(ThemeDefinition::id)

    /** The theme with [id], or the default when it's unknown (for example after a theme is retired). */
    fun theme(id: String?): ThemeDefinition = byId[id] ?: byId.getValue(defaultThemeId)

    fun areaColor(id: String): AreaColor? = areaColors.firstOrNull { it.id == id }

    companion object {
        fun parse(json: String): DesignTokens {
            val root = Json.parseToJsonElement(json).jsonObject
            val themes = root.obj("themes").map { (id, element) -> theme(id, element.jsonObject) }
            val areas = root.obj("areaPalette").getValue("colors").jsonArray.map { element ->
                val area = element.jsonObject
                AreaColor(
                    id = area.text("id"),
                    swatch = parseColor(area.text("swatch")),
                    light = chip(area.obj("light")),
                    dark = chip(area.obj("dark")),
                )
            }
            val phone = root.obj("density").obj("phone")
            val motion = root.obj("motion")
            return DesignTokens(
                defaultThemeId = root.text("defaultTheme"),
                themes = themes,
                areaColors = areas,
                density = Density(phone.int("rowMinHeight"), phone.int("rowGap"), phone.int("pagePadding"), phone.int("cardPadding")),
                motion = MotionTokens(motion.int("quick"), motion.int("standard"), motion.int("emphasized")),
            )
        }

        private fun theme(id: String, theme: JsonObject): ThemeDefinition {
            val typography = theme.obj("typography")
            val shape = theme.obj("shape")
            val dark = theme.obj("dark")
            return ThemeDefinition(
                id = id,
                name = theme.text("name"),
                summary = theme.text("summary"),
                typography = ThemeTypography(
                    heading = typeStyle(typography.obj("heading")),
                    body = typography.obj("body").let { body ->
                        BodyStyle(body.text("family"), body.int("weight"), body.int("strongWeight"), body.float("width"))
                    },
                    number = typeStyle(typography.obj("number")),
                ),
                shapes = ThemeShapes(shape.int("card"), shape.int("row"), shape.int("checkbox"), shape.int("button")),
                light = palette(theme.obj("light")),
                dark = palette(dark),
                black = palette(JsonObject(dark + theme.obj("black"))),
                logo = theme.obj("logo").let { logo -> LogoColors(logo.color("tile"), logo.color("letter"), logo.color("arrow")) },
            )
        }

        private fun typeStyle(style: JsonObject) = TypeStyle(
            family = style.text("family"),
            weight = style.int("weight"),
            width = style.float("width"),
            italic = style["italic"]?.jsonPrimitive?.boolean ?: false,
            uppercase = style["uppercase"]?.jsonPrimitive?.boolean ?: false,
            tracking = style["tracking"]?.jsonPrimitive?.float ?: 0f,
        )

        private fun palette(roles: JsonObject) = Palette(
            background = roles.color("background"),
            surface = roles.color("surface"),
            surfaceVariant = roles.color("surfaceVariant"),
            text = roles.color("text"),
            textMuted = roles.color("textMuted"),
            outline = roles.color("outline"),
            primary = roles.color("primary"),
            onPrimary = roles.color("onPrimary"),
            accent = roles.color("accent"),
            onAccent = roles.color("onAccent"),
            hero = roles.color("hero"),
            onHero = roles.color("onHero"),
            heroAccent = roles.color("heroAccent"),
            danger = roles.color("danger"),
        )

        private fun chip(pair: JsonObject) = ChipColors(pair.color("container"), pair.color("content"))

        /** "#RRGGBB" as an opaque ARGB integer. */
        fun parseColor(hex: String): Int {
            require(hex.length == 7 && hex[0] == '#') { "Not a #RRGGBB color: $hex" }
            return (0xFF000000 or hex.substring(1).toLong(16)).toInt()
        }

        private fun JsonObject.obj(name: String) = getValue(name).jsonObject

        private fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.content

        private fun JsonObject.int(name: String) = getValue(name).jsonPrimitive.int

        private fun JsonObject.float(name: String) = getValue(name).jsonPrimitive.float

        private fun JsonObject.color(name: String): Int = parseColor(text(name))
    }
}
