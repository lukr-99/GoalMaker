package com.goalmaker.app.ui.lifegoals

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.goalmaker.app.ui.theme.AppTheme
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One life goal picture, cropped to fill [modifier]'s box. [load] reads its bytes (null while the
 * file is on its way, which shows a quiet placeholder); [version] makes it look again once files move.
 */
@Composable
fun PictureImage(
    id: String,
    version: Int,
    description: String,
    load: suspend (String) -> ByteArray?,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(id, version) {
        if (bitmap == null) bitmap = load(id)?.let { bytes -> withContext(Dispatchers.Default) { decode(bytes) } }
    }
    val shown = bitmap
    if (shown != null) {
        Image(
            bitmap = shown,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.background(AppTheme.colors.surfaceVariant).semantics { contentDescription = description },
        ) {
            Icon(Icons.Outlined.Image, contentDescription = null, tint = AppTheme.colors.textMuted)
        }
    }
}

// A card is about a phone's width, so a picture is read at half size when it is far bigger.
private fun decode(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val sample = if (max(bounds.outWidth, bounds.outHeight) > DISPLAY_SIDE * 2) 2 else 1
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?.asImageBitmap()
}

private const val DISPLAY_SIDE = 1080
