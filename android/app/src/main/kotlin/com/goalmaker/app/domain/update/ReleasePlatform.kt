package com.goalmaker.app.domain.update

/** A platform that has its own artifact in a release. [wireName] is the manifest value. */
enum class ReleasePlatform(val wireName: String) {
    ANDROID("android"),
    WINDOWS("windows"),
    ;

    companion object {
        fun fromWireName(value: String): ReleasePlatform? = entries.firstOrNull { it.wireName == value }
    }
}
