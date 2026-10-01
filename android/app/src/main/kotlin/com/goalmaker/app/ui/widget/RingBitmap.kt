package com.goalmaker.app.ui.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * A progress ring as a picture, for the Goals widget: Glance has no ring that shows an amount, so
 * the widget draws one the way the app's ProgressRing looks, a faint track and an arc from the top.
 */
object RingBitmap {
    fun draw(fraction: Double, track: Int, color: Int, sizePx: Int, strokePx: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = strokePx / 2f
        val bounds = RectF(inset, inset, sizePx - inset, sizePx - inset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
        }
        paint.color = track
        canvas.drawArc(bounds, 0f, 360f, false, paint)
        val sweep = (fraction.coerceIn(0.0, 1.0) * 360.0).toFloat()
        if (sweep > 0f) {
            paint.color = color
            canvas.drawArc(bounds, -90f, sweep, false, paint)
        }
        return bitmap
    }
}
