package com.simplecityapps.shuttle.ui.common.view

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.simplecityapps.shuttle.R

class BottomSheetOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CoordinatorLayout(context, attrs, defStyleAttr) {

    interface OnBottomSheetStateChangeListener {
        fun onStateChanged(@BottomSheetBehavior.State state: Int)
    }

    private var settingsBottomSheetBackgroundAnimation: ValueAnimator? = null

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var backgroundView: View

    var listener: OnBottomSheetStateChangeListener? = null

    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onSlide(bottomSheet: View, offset: Float) {
            if (settingsBottomSheetBackgroundAnimation?.isRunning != true) {
                var alpha = 1f - (offset * -1f)
                if (alpha.isNaN()) {
                    alpha = 1f
                }
                backgroundView.alpha = alpha
            }
        }

        override fun onStateChanged(bottomSheet: View, state: Int) {
            if (state == BottomSheetBehavior.STATE_HIDDEN) {
                settingsBottomSheetBackgroundAnimation?.cancel()
                settingsBottomSheetBackgroundAnimation = backgroundView.fadeOut {
                    isVisible = false
                }
            }

            listener?.onStateChanged(state)
        }
    }

    @BottomSheetBehavior.State
    val state: Int
        get() = bottomSheetBehavior.state

    init {
        View.inflate(context, R.layout.view_bottom_sheet_overlay, this)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        backgroundView = findViewById(R.id.backgroundView)
        backgroundView.setOnClickListener { hide() }

        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottomDrawerFragment))
        bottomSheetBehavior.setBottomSheetCallback(bottomSheetCallback)
    }

    fun show() {
        isVisible = true
        settingsBottomSheetBackgroundAnimation?.cancel()
        settingsBottomSheetBackgroundAnimation = backgroundView.fadeIn()
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    fun hide(animate: Boolean = true) {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        settingsBottomSheetBackgroundAnimation?.cancel()

        if (animate) {
            settingsBottomSheetBackgroundAnimation = backgroundView.fadeOut()
        } else {
            isVisible = false
        }
    }
}