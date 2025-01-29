package com.simplecityapps.shuttle.ui.common.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.res.use
import androidx.core.math.MathUtils.clamp
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.utils.dp
import kotlin.math.abs

class HorizontalSeekbar
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    interface Listener {
        fun onProgressChanged(progress: Float) {}

        fun onStopTracking(progress: Float)
    }

    var listener: Listener? = null

    var progress: Float = 0.5f

    private val trackWidth = 4f.dp
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val rect = RectF()

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbRadius = 6f.dp

    private var initialX = 0f
    private var initialY = 0f

    private val guideLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaintStrokeWidth = 1f.dp
    var numLines = 24
    private val numGuidelines = numLines / 2

    private var progressColorEnabled: Int = 0
    private var progressColorDisabled: Int = 0

    private var trackColorEnabled: Int = 0
    private val trackColorDisabled: Int

    init {
        isActivated = true

        trackColorDisabled = ResourcesCompat.getColor(resources, R.color.vertical_seekbar_track_disabled, context.theme)

        TypedValue().apply {
            context.obtainStyledAttributes(
                data,
                intArrayOf(
                    androidx.appcompat.R.attr.colorPrimary,
                    R.attr.colorPrimaryTransparent,
                    R.attr.colorPrimarySemiTransparent
                )
            ).use { typedArray ->
                progressColorEnabled = typedArray.getColor(0, ResourcesCompat.getColor(resources, R.color.colorPrimary, context.theme))
                progressColorDisabled = typedArray.getColor(1, ResourcesCompat.getColor(resources, R.color.colorPrimaryTransparent, context.theme))
                trackColorEnabled = typedArray.getColor(2, ResourcesCompat.getColor(resources, R.color.colorPrimarySemiTransparent, context.theme))
            }
        }
        trackPaint.color = if (isActivated) trackColorEnabled else trackColorDisabled
        progressPaint.color = if (isActivated) progressColorEnabled else progressColorDisabled

        thumbPaint.color = resources.getColor(R.color.vertical_seekbar_thumb)
        thumbPaint.setShadowLayer(2f.dp, 0f, 2f.dp, 0x40000000)

        guideLinePaint.strokeWidth = 1f.dp
        guideLinePaint.strokeCap = Paint.Cap.ROUND

        TypedValue().apply {
            context.theme.resolveAttribute(R.attr.guidelineColor, this, true)
            guideLinePaint.color = ResourcesCompat.getColor(resources, resourceId, context.theme)
        }

        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = event.x
                    initialY = event.y

                    // If we're more than 4 x thumb radius from the center of the thumb, prevent interaction
                    if (abs(event.x - progress * (width - thumbRadius * 2)) > thumbRadius * 4) {
                        return@setOnTouchListener false
                    }
                }
                MotionEvent.ACTION_MOVE -> {

                    progress = mRound(clamp(event.x / width, 0f, 1f), 1f / numLines)

                    invalidate()

                    // If we're moving generally along the vertical axis, prevent the parent from scrolling
                    if (abs(event.x - initialX) > abs(event.y - initialY)) {
                        parent.requestDisallowInterceptTouchEvent(true)
                    }

                    listener?.onProgressChanged(progress)
                }
                MotionEvent.ACTION_UP -> {
                    listener?.onStopTracking(progress)
                }
            }

            true
        }
    }

    override fun setActivated(activated: Boolean) {
        super.setActivated(activated)

        trackPaint.color = if (activated) trackColorEnabled else trackColorDisabled
        progressPaint.color = if (activated) progressColorEnabled else progressColorDisabled

        invalidate()
    }

    /**
     * Rounds i to nearest multiple of n
     */
    private fun mRound(
        i: Float,
        n: Float
    ): Float = if ((i % n) > n / 2) (i + n - i % n) else (i - i % n)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Guidelines
        for (i in 0..numGuidelines) {
            if (i == 0 || i == numGuidelines || i == numGuidelines / 2) {
                guideLinePaint.strokeWidth = linePaintStrokeWidth * 2
            } else {
                guideLinePaint.strokeWidth = linePaintStrokeWidth
            }
            canvas.drawLine(
                (thumbRadius) + i * ((width - thumbRadius * 2) / numGuidelines.toFloat()),
                0f,
                (thumbRadius) + i * ((width - thumbRadius * 2) / numGuidelines.toFloat()),
                height.toFloat(),
                guideLinePaint
            )
        }

        // Track
        rect.set(
            thumbRadius,
            (height / 2f - trackWidth / 2f),
            width.toFloat() - thumbRadius,
            height / 2f + trackWidth / 2f
        )
        canvas.drawRoundRect(rect, trackWidth / 2, trackWidth / 2, trackPaint)

        // Progress
        rect.set(
            width * progress,
            (height / 2f - trackWidth / 2f),
            width / 2f,
            height / 2f + trackWidth / 2f
        )
        canvas.drawRoundRect(rect, trackWidth / 2, trackWidth / 2, progressPaint)

        // Thumb
        rect.set(
            (width - thumbRadius * 2) * progress,
            (height / 2f - thumbRadius),
            (width - thumbRadius * 2) * progress + thumbRadius * 2,
            height / 2f + thumbRadius
        )
        canvas.drawRoundRect(rect, thumbRadius, thumbRadius, thumbPaint)
    }
}
