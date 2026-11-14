package com.goalmaker.app.ui.about

import com.goalmaker.app.R

/**
 * What the phone app ships that is not its own code, as android/gradle/libs.versions.toml and fonts/
 * list it, with each licence. Keep it in step when a library comes or goes (docs/about.md).
 */
object AboutCredits {
    const val OFL = "SIL OFL 1.1"
    const val APACHE = "Apache 2.0"

    val groups: List<AboutCreditGroup> = listOf(
        AboutCreditGroup(
            R.string.about_credits_licence,
            listOf(AboutCredit("GoalMaker", R.string.about_credit_goalmaker, "PolyForm Noncommercial 1.0.0")),
        ),
        AboutCreditGroup(
            R.string.about_credits_fonts,
            listOf(
                AboutCredit("Archivo", R.string.about_credit_archivo, OFL),
                AboutCredit("Plus Jakarta Sans", R.string.about_credit_jakarta, OFL),
                AboutCredit("Space Grotesk", R.string.about_credit_grotesk, OFL),
                AboutCredit("Outfit", R.string.about_credit_outfit, OFL),
            ),
        ),
        AboutCreditGroup(
            R.string.about_credits_icons,
            listOf(AboutCredit("Material Icons", R.string.about_credit_material_icons, APACHE)),
        ),
        AboutCreditGroup(
            R.string.about_credits_libraries,
            listOf(
                AboutCredit("Android Jetpack", R.string.about_credit_jetpack, APACHE),
                AboutCredit("Kotlin coroutines and serialization", R.string.about_credit_kotlinx, APACHE),
                AboutCredit("supabase-kt", R.string.about_credit_supabase, "MIT"),
                AboutCredit("Ktor", R.string.about_credit_ktor, APACHE),
                AboutCredit("Konfetti", R.string.about_credit_konfetti, "ISC"),
                AboutCredit("Google code scanner", R.string.about_credit_code_scanner, "Google APIs terms"),
            ),
        ),
    )
}
