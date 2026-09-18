package com.goalmaker.app.ui.components

import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.goalmaker.app.R

/** The optional completion tick (tools/generate_tick_sound.py), played as a UI sound, not media. */
@Composable
fun rememberTickSound(): () -> Unit {
    val context = LocalContext.current
    val pool = remember {
        SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
    }
    val sound = remember(pool) { pool.load(context, R.raw.tick, 1) }
    DisposableEffect(pool) { onDispose { pool.release() } }
    return remember(pool, sound) { { pool.play(sound, 0.6f, 0.6f, 1, 0, 1f) } }
}
