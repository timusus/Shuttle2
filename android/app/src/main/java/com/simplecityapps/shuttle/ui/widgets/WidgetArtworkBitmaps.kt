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
import kotlin.math.max

/**
 * Decoded widget artwork, shared across the layouts of one render.
 *
 * Glance composes every size the launcher reports separately, and RemoteViews only stores a bitmap once when
 * each size hands it the same instance, so decoding per composable would multiply the memory sent to the
 * launcher. The saved file is sized for the largest layout; smaller layouts get a subsampled decode.
 */
internal object WidgetArtworkBitmaps {
    private val cache = LruCache<String, Bitmap>(4)

    /**
     * The saved artwork, decoded at the smallest power-of-two subsample that still covers [widthPx] by
     * [heightPx]. Used on API 31+, where the widget clips the corners itself.
     */
    fun sized(
        path: String,
        widthPx: Int,
        heightPx: Int
    ): Bitmap? = synchronized(this) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sampleSize = sampleSize(minOf(bounds.outWidth, bounds.outHeight), max(widthPx, heightPx))
        val key = "$path#$sampleSize"
        cache[key] ?: BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize })?.also { cache.put(key, it) }
    }

    /**
     * The artwork centre-cropped to [widthPx] by [heightPx] with its corners rounded. Below API 31 widgets can't
     * clip views, so the corners are drawn into the bitmap at the size it's shown.
     */
    fun rounded(
        path: String,
        widthPx: Int,
        heightPx: Int,
        radiusPx: Float
    ): Bitmap? = synchronized(this) {
        val key = "$path@${widthPx}x$heightPx"
        cache[key] ?: sized(path, widthPx, heightPx)?.let { roundedCopy(it, widthPx, heightPx, radiusPx) }?.also { cache.put(key, it) }
    }

    private fun sampleSize(
        sourceEdge: Int,
        targetEdge: Int
    ): Int {
        var sample = 1
        while (sourceEdge / (sample * 2) >= targetEdge) sample *= 2
        return sample
    }

    private fun roundedCopy(
        source: Bitmap,
        widthPx: Int,
        heightPx: Int,
        radiusPx: Float
    ): Bitmap {
        val output = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        // Centre crop: scale to cover, then centre the overflow.
        val scale = max(widthPx / source.width.toFloat(), heightPx / source.height.toFloat())
        shader.setLocalMatrix(
            Matrix().apply {
                setScale(scale, scale)
                postTranslate((widthPx - source.width * scale) / 2f, (heightPx - source.height * scale) / 2f)
            }
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
        Canvas(output).drawRoundRect(RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()), radiusPx, radiusPx, paint)
        return output
    }
}
