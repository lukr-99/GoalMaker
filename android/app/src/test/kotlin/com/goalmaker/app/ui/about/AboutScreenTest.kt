package com.goalmaker.app.ui.about

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.goalmaker.app.application.about.AppInfo
import com.goalmaker.app.application.environment.BackendEnvironment
import com.goalmaker.app.domain.about.AuthorLinks
import com.goalmaker.app.domain.design.DesignTokens
import com.goalmaker.app.domain.design.LogoMark
import com.goalmaker.app.domain.settings.Appearance
import com.goalmaker.app.domain.settings.ReduceMotion
import com.goalmaker.app.domain.settings.ThemeMode
import com.goalmaker.app.ui.theme.GoalMakerTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The About page (docs/about.md): the author card's three links open their addresses, and the build's
 * facts show. With GOALMAKER_SNAPSHOTS set it also writes the page in every theme, light and dark, as
 * PNG files there, for a look without a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h2400dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AboutScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val opened = mutableListOf<String>()
    private var appearance by mutableStateOf(
        Appearance.DEFAULT.copy(mode = ThemeMode.LIGHT, reduceMotion = ReduceMotion.ON),
    )

    private fun show(info: AppInfo = RELEASE) {
        rule.setContent {
            GoalMakerTheme(TOKENS, appearance, logo = LOGO) {
                CompositionLocalProvider(
                    LocalUriHandler provides object : UriHandler {
                        override fun openUri(uri: String) {
                            opened += uri
                        }
                    },
                ) {
                    AboutScreen(info = info, onBack = {})
                }
            }
        }
    }

    @Test
    fun `each link opens its address`() {
        show()

        rule.onNode(hasContentDescription("Instagram, @lukas9krejci")).performClick()
        rule.onNode(hasContentDescription("GitHub, lukr-99")).performClick()
        rule.onNode(hasContentDescription("Report a bug or an idea, lukr-99/GoalMaker")).performClick()

        assertEquals(AuthorLinks.all.map { it.url }, opened)
    }

    @Test
    fun `the build's facts and the author show`() {
        show()

        // Track sets headings in upper case.
        rule.onNodeWithText("Lukáš Krejčí", ignoreCase = true).assertExists()
        rule.onNodeWithText("https://example.supabase.co").performScrollTo()
        rule.onNodeWithText("Release").assertExists()
    }

    @Test
    fun `a local only build says everything stays on the phone`() {
        show(RELEASE.copy(isDevBuild = true, localOnly = true))

        rule.onNodeWithText("This build is local only", substring = true).assertExists()
        rule.onNodeWithText("It syncs to the backend", substring = true).assertDoesNotExist()
    }

    @Test
    fun `snapshots in every theme`() {
        val folder = System.getenv("GOALMAKER_SNAPSHOTS")
        assumeTrue("Set GOALMAKER_SNAPSHOTS to write the snapshots", !folder.isNullOrBlank())
        show()
        listOf("track", "electric", "night", "sunrise").forEach { id ->
            listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
                appearance = appearance.copy(themeId = id, mode = mode)
                rule.waitForIdle()
                val image = rule.onRoot().captureToImage().asAndroidBitmap()
                File(folder!!, "android-about-$id-${mode.name.lowercase()}.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }

    private companion object {
        val BACKEND = BackendEnvironment("https://example.supabase.co", "key")
        val RELEASE = AppInfo(versionName = "1.16.0", isDevBuild = false, backend = BACKEND, defaultBackend = BACKEND)
        private val contracts = File(System.getProperty("goalmaker.contracts")!!)
        val TOKENS: DesignTokens by lazy { DesignTokens.parse(File(contracts, "design/themes.json").readText()) }
        val LOGO: LogoMark by lazy { LogoMark.parse(File(contracts, "design/logo.json").readText()) }
    }
}
