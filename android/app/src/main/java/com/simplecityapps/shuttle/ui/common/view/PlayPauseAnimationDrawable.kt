package com.simplecityapps.shuttle.ui.common.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.Property
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.utils.dp

class PlayPauseAnimationDrawable(context: Context) : Drawable() {
    private var color = Color.WHITE
    private val mLeftPauseBar = Path()
    private val mRightPauseBar = Path()
    private val mPaint = Paint()
    private val mBounds = RectF()
    private val mPauseBarWidth: Float
    private val mPauseBarHeight: Float
    private val mPauseBarDistance: Float
    private var mWidth = 0f
    private var mHeight = 0f
    private var mProgress = 0f

    var isPlay = false

    val pausePlayAnimator: Animator
        get() {
            val anim: Animator = ObjectAnimator.ofFloat(this, PROGRESS, if (isPlay) 1f else 0f, if (isPlay) 0f else 1f)
            anim.addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        isPlay = !isPlay
                    }
                }
            )
            return anim
        }

    var progress: Float
        get() = mProgress
        set(progress) {
            mProgress = progress
            invalidateSelf()
        }

    init {
        val res = context.resources
        mPaint.isAntiAlias = true
        mPaint.style = Paint.Style.FILL
        mPaint.color = color
        mPauseBarWidth = res.getDimensionPixelSize(R.dimen.pause_bar_width).toFloat()
        mPauseBarHeight = res.getDimensionPixelSize(R.dimen.pause_bar_height).toFloat()
        mPauseBarDistance = res.getDimensionPixelSize(R.dimen.pause_bar_distance).toFloat()
    }

    fun setColor(color: Int) {
        this.color = color
        mPaint.color = color
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        mBounds.set(bounds)
        mWidth = mBounds.width()
        mHeight = mBounds.height()
    }

    override fun draw(canvas: Canvas) {
        mLeftPauseBar.rewind()
        mRightPauseBar.rewind()

        // The current distance between the two pause bars.
        val barDist = lerp(mPauseBarDistance, -1f, mProgress)
        // The current width of each pause bar.
        val barWidth = lerp(mPauseBarWidth, mPauseBarHeight / 2f, mProgress)
        // The current position of the left pause bar's top left coordinate.
        val firstBarTopLeft = lerp(0f, barWidth, mProgress)
        // The current position of the right pause bar's top right coordinate.
        val secondBarTopRight = lerp(2 * barWidth + barDist, barWidth + barDist, mProgress)
        // The new 'height' of the pause bar (which translates to width of triangle)
        val pauseBarHeight = lerp(mPauseBarHeight, mPauseBarHeight - 2.5f.dp, mProgress)

        // Draw the left pause bar. The left pause bar transforms into the
        // top half of the play button triangle by animating the position of the
        // rectangle's top left coordinate and expanding its bottom width.
        mLeftPauseBar.moveTo(0f, 0f)
        mLeftPauseBar.lineTo(firstBarTopLeft, -pauseBarHeight)
        mLeftPauseBar.lineTo(barWidth, -pauseBarHeight)
        mLeftPauseBar.lineTo(barWidth, 0f)
        mLeftPauseBar.close()

        // Draw the right pause bar. The right pause bar transforms into the
        // bottom half of the play button triangle by animating the position of the
        // rectangle's top right coordinate and expanding its bottom width.
        mRightPauseBar.moveTo(barWidth + barDist, 0f)
        mRightPauseBar.lineTo(barWidth + barDist, -pauseBarHeight)
        mRightPauseBar.lineTo(secondBarTopRight, -pauseBarHeight)
        mRightPauseBar.lineTo(2 * barWidth + barDist, 0f)
        mRightPauseBar.close()
        canvas.save()

        // Translate the play button a tiny bit to the right so it looks more centered.
        canvas.translate(lerp(0f, 4f.dp, mProgress), 0f)

        // (1) Pause --> Play: rotate 0 to 90 degrees clockwise.
        // (2) Play --> Pause: rotate 90 to 180 degrees clockwise.
        val rotationProgress = if (isPlay) mProgress else 1 - mProgress
        val startingRotation: Float = if (isPlay) 0f else 90f
        canvas.rotate(lerp(startingRotation, startingRotation + 90, rotationProgress), mWidth / 2f, mHeight / 2f)

        // Position the pause/play button in the center of the drawable's bounds.
        canvas.translate(mWidth / 2f - (2 * barWidth + barDist) / 2f, mHeight / 2f + mPauseBarHeight / 2f)

        // Draw the two bars that form the animated pause/play button.
        canvas.drawPath(mLeftPauseBar, mPaint)
        canvas.drawPath(mRightPauseBar, mPaint)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        mPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(cf: ColorFilter?) {
        mPaint.colorFilter = cf
        invalidateSelf()
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    companion object {
        private val PROGRESS: Property<PlayPauseAnimationDrawable, Float> =
            object : Property<PlayPauseAnimationDrawable, Float>(Float::class.java, "progress") {
                override fun get(d: PlayPauseAnimationDrawable): Float {
                    return d.progress
                }

                override fun set(
                    d: PlayPauseAnimationDrawable,
                    value: Float
                ) {
                    d.progress = value
                }
            }

        /**
         * Linear interpolate between a and b with parameter t.
         */
        private fun lerp(
            a: Float,
            b: Float,
            t: Float
        ): Float {
            return a + (b - a) * t
        }
    }
}
