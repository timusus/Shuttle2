package com.simplecityapps.shuttle.ui.common.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.FrameLayout
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.ui.common.utils.dp

class KeylineView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val enabled = false

    private val paint = Paint()

    private val gutterWidth = 16f.dp
    private val gutterFillColor = 0xFFE5FBEF.toInt()

    private val horizontalKeylineStart = 72f.dp
    private val horizontalKeyLineColor = 0xFFC68BA4.toInt()
    private val horizontalKeyLineWidth = 2f.dp

    private val verticalKeylineStart = 56f.dp
    private val verticalKeyLineColor = 0xFFC68BA4.toInt()
    private val verticalKeyLineWidth = 2f.dp

    private val rect = RectF()

    init {
        setWillNotDraw(!BuildConfig.DEBUG || !enabled)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!BuildConfig.DEBUG || !enabled) {
            return
        }

        // Left gutter
        rect.set(0f, 0f, gutterWidth, height.toFloat())
        paint.style = Paint.Style.FILL
        paint.color = gutterFillColor
        canvas.drawRect(rect, paint)

        // Right gutter
        rect.set(width - gutterWidth, 0f, width.toFloat(), height.toFloat())
        paint.style = Paint.Style.FILL
        paint.color = gutterFillColor
        canvas.drawRect(rect, paint)

        // Vertical Keyline 1
        paint.style = Paint.Style.STROKE
        paint.color = horizontalKeyLineColor
        paint.strokeWidth = horizontalKeyLineWidth
        canvas.drawLine(horizontalKeylineStart, 0f, horizontalKeylineStart, height.toFloat(), paint)

        // Horizontal Keyline 1
        paint.style = Paint.Style.STROKE
        paint.color = verticalKeyLineColor
        paint.strokeWidth = verticalKeyLineWidth
        canvas.drawLine(gutterWidth, verticalKeylineStart, width - gutterWidth, verticalKeylineStart, paint)

        // Vertical gridlines
        for (i in 0 until ((width.toFloat() - gutterWidth * 2) / 8f.dp).toInt()) {
            paint.strokeWidth = 1f.dp
            canvas.drawLine(gutterWidth + i * 8f.dp, 0f, gutterWidth + i * 8f.dp, height.toFloat(), paint)
        }

        // Horizontal gridlines
        for (i in 0 until ((height.toFloat()) / 8f.dp).toInt()) {
            paint.strokeWidth = 1f.dp
            canvas.drawLine(gutterWidth, i * 8f.dp, width - gutterWidth, i * 8f.dp, paint)
        }
    }
}
