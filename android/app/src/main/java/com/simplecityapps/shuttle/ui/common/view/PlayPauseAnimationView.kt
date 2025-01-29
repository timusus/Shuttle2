package com.simplecityapps.shuttle.ui.common.view

import android.animation.Animator
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.content.res.use
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.shuttle.R
import kotlin.math.min

class PlayPauseAnimationView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val animationDrawable: PlayPauseAnimationDrawable

    private var animator: Animator? = null

    private var drawableColor: Int = Color.WHITE

    var playbackState: PlaybackState = PlaybackState.Paused
        set(value) {
            field = value
            update()
        }

    init {
        setWillNotDraw(false)

        animationDrawable = PlayPauseAnimationDrawable(context)
        animationDrawable.callback = this

        context.theme.obtainStyledAttributes(attrs, R.styleable.PlayPauseView, 0, 0).use { typedArray ->
            drawableColor = typedArray.getColor(R.styleable.PlayPauseView_android_tint, Color.WHITE)
            animationDrawable.setColor(drawableColor)
        }

        update()
    }

    private fun update() {
        when (playbackState) {
            PlaybackState.Playing -> {
                if (animationDrawable.isPlay) {
                    toggleAnimation()
                }
            }
            PlaybackState.Paused -> {
                if (!animationDrawable.isPlay) {
                    toggleAnimation()
                }
            }
            PlaybackState.Loading -> {
            }
        }
    }

    private fun toggleAnimation() {
        if (animator != null) {
            animator!!.cancel()
        }
        animator = animationDrawable.pausePlayAnimator
        animator!!.interpolator = DecelerateInterpolator()
        animator!!.duration = PLAY_PAUSE_ANIMATION_DURATION
        animator!!.start()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val size = min(measuredWidth, measuredHeight)
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        animationDrawable.setBounds(0, 0, w, h)

        outlineProvider =
            object : ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                override fun getOutline(
                    view: View,
                    outline: Outline
                ) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
        clipToOutline = true
    }

    override fun verifyDrawable(who: Drawable): Boolean = who === animationDrawable || super.verifyDrawable(who)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        animationDrawable.draw(canvas)
    }

    companion object {
        private const val PLAY_PAUSE_ANIMATION_DURATION: Long = 200
    }
}
