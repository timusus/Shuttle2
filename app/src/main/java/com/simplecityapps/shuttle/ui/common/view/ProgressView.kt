package com.simplecityapps.shuttle.ui.common.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import com.simplecityapps.shuttle.R

class ProgressView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint: Paint

    private val rect: RectF = RectF()

    private var progress: Float = 0f

    init {
        setWillNotDraw(false)

        paint = Paint(Paint.ANTI_ALIAS_FLAG)

        TypedValue().apply {
            context.obtainStyledAttributes(data, intArrayOf(R.attr.colorAccent)).use { typedArray ->
                paint.color = typedArray.getColor(0, ContextCompat.getColor(context, R.color.colorPrimary))
            }
        }

        if (isInEditMode) {
            progress = 0.66f
        }

        setBackgroundColor(Color.argb(10, 0, 0, 0))
    }

    fun setProgress(progress: Float) {
        this.progress = progress
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        rect.set(0f, 0f, width * progress, height.toFloat())
        canvas.drawRoundRect(rect, height / 2f, height / 2f, paint)
    }
}