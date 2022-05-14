package com.simplecityapps.shuttle.ui.common.view

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.TargetApi
import android.os.Build
import android.view.View
import android.view.ViewAnimationUtils
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.view.isInvisible
import androidx.transition.TransitionListenerAdapter

@TargetApi(Build.VERSION_CODES.O)
class DetailImageAnimationHelper(
    private val heroView: View,
    private val dummyView: View
) : TransitionListenerAdapter() {

    private val duration = 200L
    private val delay = duration / 5

    fun showHeroView() {
        heroView.isInvisible = false

        val scaleX = dummyView.width.toFloat() / heroView.width
        val scaleXAnim = ObjectAnimator.ofFloat(scaleX, 1f)
        scaleXAnim.addUpdateListener { animator ->
            heroView.scaleX = animator.animatedValue as Float
        }

        val scaleY = dummyView.height.toFloat() / heroView.height
        val scaleYAnim = ObjectAnimator.ofFloat(scaleY, 1f)
        scaleYAnim.addUpdateListener { animator ->
            heroView.scaleY = animator.animatedValue as Float
        }

        val delayAnim = ViewAnimationUtils.createCircularReveal(heroView, heroView.width / 2, heroView.height / 2, dummyView.width.toFloat() * 4.5f, dummyView.height.toFloat() * 4.5f)
        delayAnim.duration = delay

        val revealAnim = ViewAnimationUtils.createCircularReveal(heroView, heroView.width / 2, heroView.height / 2, dummyView.width.toFloat() * 4.5f, heroView.height.toFloat())
        revealAnim.startDelay = delay

        val animationSet = AnimatorSet()
        animationSet.duration = duration
        animationSet.interpolator = AccelerateDecelerateInterpolator()
        animationSet.playTogether(delayAnim, revealAnim, scaleXAnim, scaleYAnim)
        animationSet.start()
    }
}
