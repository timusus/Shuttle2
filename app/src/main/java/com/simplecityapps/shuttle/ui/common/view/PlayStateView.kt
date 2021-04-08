package com.simplecityapps.shuttle.ui.common.view

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.core.content.res.use
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.utils.dp

class PlayStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val progressBar: ProgressBar

    private val playPauseAnimationView: PlayPauseAnimationView

    private var animator: ValueAnimator? = null

    var playbackState: PlaybackState = PlaybackState.Loading
        set(value) {
            if (field != value) {
                field = value
                update()
            }
        }

    init {
        val drawableColor = context.theme.obtainStyledAttributes(attrs, R.styleable.PlayPauseLoadView, 0, 0)
            .use { typedArray ->
                typedArray.getColor(R.styleable.PlayPauseLoadView_android_tint, Color.WHITE)
            }

        progressBar = ProgressBar(context, attrs)
            .apply {
                id = generateViewId()
                indeterminateTintList = ColorStateList.valueOf(drawableColor)
                setPadding(12.dp, 12.dp, 12.dp, 12.dp)
            }

        playPauseAnimationView = PlayPauseAnimationView(context, attrs)

        addView(progressBar)
        addView(playPauseAnimationView)

        update()
    }

    fun update() {
        playPauseAnimationView.playbackState = playbackState
        when (playbackState) {
            PlaybackState.Loading -> {
                animator?.cancel()

                animator = playPauseAnimationView.fadeOut(delay = 500) {
                    progressBar.fadeIn()
                }
            }
            PlaybackState.Paused -> {
                animator?.cancel()
                progressBar.fadeOut {
                    playPauseAnimationView.fadeIn()
                }
            }
            PlaybackState.Playing -> {
                animator?.cancel()
                progressBar.fadeOut {
                    playPauseAnimationView.fadeIn()
                }
            }
        }
    }
}