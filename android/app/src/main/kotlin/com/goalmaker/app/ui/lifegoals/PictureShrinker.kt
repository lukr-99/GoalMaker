package com.goalmaker.app.ui.lifegoals

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Makes a picked photo ready to keep (ADR 0018): turned the way the camera held it, at most
 * [MAX_SIDE] pixels on its longest side, and saved as a JPEG. Blocks, so callers run it off the main
 * thread. Null when the file can't be read as a picture.
 */
object PictureShrinker {
    const val MAX_SIDE = 1600
    private const val QUALITY = 85

    fun shrink(resolver: ContentResolver, uri: Uri): ShrunkPicture? = runCatching {
        val bitmap = decode(resolver, uri) ?: return null
        val scale = MAX_SIDE.toFloat() / max(bitmap.width, bitmap.height)
        val sized = if (scale < 1f) {
            bitmap.scale((bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt())
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        sized.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        ShrunkPicture(out.toByteArray(), sized.width, sized.height)
    }.getOrNull()

    private fun decode(resolver: ContentResolver, uri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder turns the picture the way its EXIF says and samples it down while it reads.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_SIDE * 2) decoder.setTargetSampleSize(longest / (MAX_SIDE * 2) + 1)
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val longest = max(bounds.outWidth, bounds.outHeight)
            val options = BitmapFactory.Options().apply { inSampleSize = (longest / (MAX_SIDE * 2) + 1).coerceAtLeast(1) }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
}
