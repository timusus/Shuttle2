package com.simplecityapps.shuttle.ui.common.view

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.utils.dp

class HorizontalLoadingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var currentState: State? = null

    private var animation: ValueAnimator? = null

    private lateinit var textView: TextView
    private lateinit var progressBar: ProgressBar

    init {
        View.inflate(context, R.layout.view_loading_horizontal, this)

        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(16.dp, 16.dp, 16.dp, 16.dp)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        textView = findViewById(R.id.textView)
        progressBar = findViewById(R.id.progressBar)
    }

    fun setState(state: State) {
        if (state != currentState) {
            when (state) {
                is State.Loading -> {
                    animation?.cancel()
                    animation = fadeOut(completion = { animation = fadeIn() })

                    textView.text = state.message
                }
                is State.None -> {
                    animation?.cancel()
                    animation = fadeOut()
                }
            }
            currentState = state
        }
    }

    fun setProgress(progress: Float) {
        progressBar.progress = (progress * 100).toInt()
    }

    override fun onDetachedFromWindow() {
        animation?.removeAllUpdateListeners()
        animation?.cancel()
        super.onDetachedFromWindow()
    }

    sealed class State {
        data class Loading(val message: String = "Loading…") : State()
        object None : State()
    }
}