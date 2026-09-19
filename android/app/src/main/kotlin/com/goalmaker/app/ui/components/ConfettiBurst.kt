package com.goalmaker.app.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.goalmaker.app.ui.theme.AppTheme
import java.util.concurrent.TimeUnit
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.compose.OnParticleSystemUpdateListener
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.PartySystem
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter

/**
 * One burst of confetti over the screen in the theme's colors, for a goal hit or a streak milestone
 * and nothing else (design spec, level 3 of 5). Callers skip it under reduce motion; [onFinished] runs
 * once the last piece has fallen.
 */
@Composable
fun ConfettiBurst(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val palette = (listOf(colors.accent, colors.primary, colors.heroAccent) + AppTheme.areaColors.take(4).map(colors::areaContent))
        .map { it.toArgb() }
    val parties = remember(palette) {
        listOf(
            Party(
                speed = 0f,
                maxSpeed = 30f,
                damping = 0.9f,
                spread = 360,
                colors = palette,
                position = Position.Relative(0.5, 0.3),
                emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100),
            ),
        )
    }
    KonfettiView(
        modifier = modifier.fillMaxSize(),
        parties = parties,
        updateListener = object : OnParticleSystemUpdateListener {
            override fun onParticleSystemEnded(system: PartySystem, activeSystems: Int) {
                if (activeSystems == 0) onFinished()
            }
        },
    )
}
