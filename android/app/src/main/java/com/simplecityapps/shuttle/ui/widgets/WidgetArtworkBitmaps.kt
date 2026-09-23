package com.simplecityapps.shuttle.ui.widgets

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache

/**
 * Decoded widget artwork, shared across the layouts of one render.
 *
 * Glance composes every responsive size separately, and RemoteViews only stores a bitmap once when each size
 * hands it the same instance, so decoding per composable would multiply the memory sent to the launcher.
 */
internal object WidgetArtworkBitmaps {
    private val cache = LruCache<String, Bitmap>(4)

    /** The saved artwork at full size. Used on API 31+, where the widget clips the corners itself. */
    fun full(path: String): Bitmap? = synchronized(this) {
        cache[path] ?: BitmapFactory.decodeFile(path)?.also { cache.put(path, it) }
    }

    /**
     * The artwork scaled to [sizePx] with its corners rounded. Below API 31 widgets can't clip views, so the
     * corners are drawn into the bitmap at the size it's shown, keeping the radius consistent between layouts.
     */
    fun rounded(
        path: String,
        sizePx: Int,
        radiusPx: Float
    ): Bitmap? = synchronized(this) {
        val key = "$path@$sizePx"
        cache[key] ?: full(path)?.let { roundedCopy(it, sizePx, radiusPx) }?.also { cache.put(key, it) }
    }

    private fun roundedCopy(
        source: Bitmap,
        sizePx: Int,
        radiusPx: Float
    ): Bitmap {
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shader.setLocalMatrix(Matrix().apply { setScale(sizePx / source.width.toFloat(), sizePx / source.height.toFloat()) })
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
        Canvas(output).drawRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), radiusPx, radiusPx, paint)
        return output
    }
}
