package com.simplecityapps.shuttle.ui.common.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.utils.dp

class CircularProgressView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val progressPaint: Paint
    private val trackPaint: Paint

    private val rect: RectF = RectF()

    private var progress: Float = 0f

    private val strokeWidth = 2f.dp

    init {
        setWillNotDraw(false)

        progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        progressPaint.color = resources.getColor(R.color.colorPrimary)
        progressPaint.strokeWidth = strokeWidth
        progressPaint.style = Paint.Style.STROKE
        progressPaint.strokeCap = Paint.Cap.ROUND

        trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        trackPaint.color = resources.getColor(R.color.colorPrimaryTransparent)
        trackPaint.strokeWidth = strokeWidth
        trackPaint.style = Paint.Style.STROKE

        if (isInEditMode) {
            progress = 0.15f
        }
    }

    fun setProgress(progress: Float) {
        this.progress = progress
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        rect.set(0f + strokeWidth / 2, 0f + strokeWidth / 2, width.toFloat() - strokeWidth / 2, height.toFloat() - strokeWidth / 2)
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)
        canvas.drawArc(rect, 270f, -(360f - (360f * progress)), false, progressPaint)
    }
}