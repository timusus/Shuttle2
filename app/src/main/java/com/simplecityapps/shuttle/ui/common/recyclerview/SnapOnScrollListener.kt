package com.simplecityapps.shuttle.ui.common.recyclerview

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import timber.log.Timber

class SnapOnScrollListener(
    private val snapHelper: SnapHelper,
    var behavior: Behavior = Behavior.NOTIFY_ON_SCROLL,
    var onSnapPositionChangeListener: ((Int) -> Unit)? = null
) : RecyclerView.OnScrollListener() {

    enum class Behavior {
        NOTIFY_ON_SCROLL,
        NOTIFY_ON_SCROLL_STATE_IDLE
    }

    private var fromUser = false

    private var snapPosition = RecyclerView.NO_POSITION

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        if (behavior == Behavior.NOTIFY_ON_SCROLL) {
            maybeNotifySnapPositionChange(recyclerView)
        }
    }

    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        Timber.i("newState: ${newState.stateToString()}")
        if (behavior == Behavior.NOTIFY_ON_SCROLL_STATE_IDLE && newState == RecyclerView.SCROLL_STATE_IDLE && fromUser) {
            maybeNotifySnapPositionChange(recyclerView)
        }

        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
            fromUser = true
        }
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
            fromUser = false
        }
    }

    fun Int.stateToString(): String {
        return when (this) {
            RecyclerView.SCROLL_STATE_IDLE -> "Idle"
            RecyclerView.SCROLL_STATE_DRAGGING -> "Dragging"
            RecyclerView.SCROLL_STATE_SETTLING -> "Settling"
            else -> "Unknown"
        }
    }

    private fun maybeNotifySnapPositionChange(recyclerView: RecyclerView) {
        val snapPosition = snapHelper.getSnapPosition(recyclerView)
        val snapPositionChanged = this.snapPosition != snapPosition
        if (snapPositionChanged) {
            onSnapPositionChangeListener?.invoke(snapPosition)
            this.snapPosition = snapPosition
        }
    }

    private fun SnapHelper.getSnapPosition(recyclerView: RecyclerView): Int {
        val layoutManager = recyclerView.layoutManager ?: return RecyclerView.NO_POSITION
        val snapView = findSnapView(layoutManager) ?: return RecyclerView.NO_POSITION
        return layoutManager.getPosition(snapView)
    }
}

fun RecyclerView.attachSnapHelperWithListener(
    snapHelper: SnapHelper,
    behavior: SnapOnScrollListener.Behavior = SnapOnScrollListener.Behavior.NOTIFY_ON_SCROLL,
    onSnapPositionChangeListener: ((Int) -> Unit)
) {
    snapHelper.attachToRecyclerView(this)
    val snapOnScrollListener = SnapOnScrollListener(snapHelper, behavior, onSnapPositionChangeListener)
    addOnScrollListener(snapOnScrollListener)
}