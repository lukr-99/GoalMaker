package com.goalmaker.app.ui.pcsignin

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tapping "Scan the code" crashed every release build with a NullPointerException inside Play
 * services' code scanner, because R8 broke ML Kit's component setup. Debug builds never ran R8, so
 * no other test could see it. This keeps the rules that fix it (docs/pitfalls.md).
 */
class ScannerKeepRulesTest {
    private val rules = (listOf(File("proguard-rules.pro"), File("app/proguard-rules.pro"))
        .firstOrNull { it.exists() }
        ?: error("proguard-rules.pro not found from ${File(".").absolutePath}; run the test from the android or android/app folder"))
        .readLines()
        .map { it.trim() }
        .filter { !it.startsWith("#") }

    @Test
    fun `R8 keeps the ML Kit classes the code scanner needs`() {
        assertTrue(rules.any { it.startsWith("-keep class com.google.mlkit.** {") })
    }

    @Test
    fun `R8 keeps the code scanner's own internal classes`() {
        assertTrue(rules.any { it.startsWith("-keep class com.google.android.gms.internal.mlkit_** {") })
    }
}
