package com.simplecityapps.shuttle.ui.common.view

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.utils.dp

class CircularLoadingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    interface Listener {
        fun onRetryClicked()
    }

    private var currentState: State? = null

    private var animation: ValueAnimator? = null

    private lateinit var textView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorImageView: ImageView
    private lateinit var retryButton: Button

    var listener: Listener? = null

    init {
        View.inflate(context, R.layout.view_loading, this)

        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(16.dp, 16.dp, 16.dp, 16.dp)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        textView = findViewById(R.id.textView)
        progressBar = findViewById(R.id.progressBar)
        errorImageView = findViewById(R.id.errorImageView)
        retryButton = findViewById(R.id.retryButton)

        retryButton.setOnClickListener {
            listener?.onRetryClicked()
        }
    }

    fun setState(state: State) {
        if (state != currentState) {
            when (state) {
                is State.Loading -> {
                    if (currentState is State.Loading) {
                        textView.setText(state.message)
                        errorImageView.isVisible = false
                        retryButton.isVisible = false
                        progressBar.isVisible = true
                        animation?.cancel()
                        animation = fadeIn()
                    } else {
                        animation?.cancel()
                        animation = fadeOut(completion = {
                            textView.setText(state.message)
                            errorImageView.isVisible = false
                            retryButton.isVisible = false
                            progressBar.isVisible = true
                            animation = fadeIn()
                        })
                    }
                }
                is State.Error -> {
                    animation?.cancel()
                    animation = fadeOut(completion = {
                        textView.setText(state.message)
                        errorImageView.isVisible = true
                        progressBar.isVisible = false
                        retryButton.isVisible = false
                        animation = fadeIn()
                    })
                }
                is State.Empty -> {
                    animation?.cancel()
                    animation = fadeOut(completion = {
                        textView.setText(state.message)
                        errorImageView.isVisible = true
                        progressBar.isVisible = false
                        retryButton.isVisible = false
                        animation = fadeIn()
                    })
                }
                is State.Retry -> {
                    animation?.cancel()
                    animation = fadeOut(completion = {
                        textView.setText(state.message)
                        retryButton.isVisible = true
                        errorImageView.isVisible = false
                        progressBar.isVisible = false
                        animation = fadeIn()
                    })
                }
                is State.None -> {
                    animation?.cancel()
                    animation = fadeOut()
                }
            }
            currentState = state
        }
    }

    fun tintProgressDrawable(color: Int) {
        val drawable = DrawableCompat.wrap(progressBar.indeterminateDrawable)
        DrawableCompat.setTint(drawable, color)
        progressBar.indeterminateDrawable = drawable
    }

    override fun onDetachedFromWindow() {
        animation?.removeAllUpdateListeners()
        animation?.cancel()
        super.onDetachedFromWindow()
    }

    sealed class State {
        data class Loading(val message: String) : State()
        data class Empty(val message: String) : State()
        data class Error(val message: String) : State()
        data class Retry(val message: String) : State()
        object None : State()
    }
}