package com.goalmaker.app.application.planning

/**
 * How often the why reminder comes (docs/life-goals.md), a device setting. [key] is the name in
 * contracts/vectors/life-goals.json and in the settings store; [days] is a period's length.
 */
enum class WhyFrequency(val key: String, val days: Int) {
    OFF("off", 0),
    DAILY("daily", 1),
    EVERY_3_DAYS("every-3-days", 3),
    WEEKLY("weekly", 7),
    ;

    companion object {
        /** What a device starts with. */
        val DEFAULT = WEEKLY

        fun of(key: String?): WhyFrequency = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
