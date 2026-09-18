package com.goalmaker.app.contracts

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Loads a vector file from the repository's contracts/ folder (path set by app/build.gradle.kts). */
object ContractFiles {
    fun load(relativePath: String): JsonObject {
        val root = System.getProperty("goalmaker.contracts")
            ?: error("goalmaker.contracts system property is not set; run tests through Gradle")
        val file = File(root, relativePath)
        check(file.isFile) { "Contract file missing: $file" }
        return Json.parseToJsonElement(file.readText()).jsonObject
    }
}
