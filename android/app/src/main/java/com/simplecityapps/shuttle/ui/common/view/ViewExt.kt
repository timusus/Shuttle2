package com.simplecityapps.shuttle.ui.common.view

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import androidx.core.animation.addListener
import androidx.core.view.isVisible

fun View.fadeIn(
    duration: Long = 250,
    delay: Long = 0
): ValueAnimator? {
    if (isVisible && alpha == 1f) return null

    val animator = ObjectAnimator.ofFloat(this, View.ALPHA, alpha, 1f)
    animator.duration = duration
    animator.startDelay = delay
    animator.addListener(onStart = {
        alpha = this.alpha
        isVisible = true
        animator.removeAllListeners()
    })
    animator.start()
    return animator
}

fun View.fadeOut(
    duration: Long = 250,
    delay: Long = 0,
    completion: (() -> Unit)? = null
): ValueAnimator? {
    if (!isVisible) {
        completion?.invoke()
        return null
    }
    val animator = ObjectAnimator.ofFloat(this, View.ALPHA, alpha, 0f)
    animator.duration = duration
    animator.startDelay = delay
    animator.addListener(
        onCancel = {
            animator.removeAllListeners()
        },
        onEnd = {
            isVisible = false
            completion?.invoke()
            animator.removeAllListeners()
        }
    )
    animator.start()
    return animator
}
