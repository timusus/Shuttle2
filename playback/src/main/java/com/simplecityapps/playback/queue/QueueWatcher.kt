package com.simplecityapps.playback.queue

import timber.log.Timber

interface QueueChangeCallback {

    fun onQueueChanged(){

    }

    fun onQueuePositionChanged(){

    }

    fun onShuffleChanged(){

    }

    fun onRepeatChanged(){

    }
}


class QueueWatcher : QueueChangeCallback {

    private var callbacks: MutableList<QueueChangeCallback> = mutableListOf()

    fun addCallback(callback: QueueChangeCallback) {
        callbacks.add(callback)
    }

    fun removeCallback(callback: QueueChangeCallback) {
        callbacks.remove(callback)
    }


    // QueueChangeCallback Implementation

    override fun onQueueChanged() {
        Timber.v("onQueueChanged()")
        callbacks.forEach { callback -> callback.onQueueChanged() }
    }

    override fun onQueuePositionChanged() {
        Timber.v("onQueuePositionChanged()")
        callbacks.forEach { callback -> callback.onQueuePositionChanged() }
    }

    override fun onShuffleChanged() {
        Timber.v("onShuffleChanged()")
        callbacks.forEach { callback -> callback.onShuffleChanged() }
    }

    override fun onRepeatChanged() {
        Timber.v("onRepeatChanged()")
        callbacks.forEach { callback -> callback.onRepeatChanged() }
    }
}