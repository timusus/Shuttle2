package com.simplecityapps.shuttle.debug

import timber.log.Timber

class DebugLoggingTree : Timber.DebugTree() {

    interface Callback {
        fun onLog(logMessage: LogMessage)
    }

    var callbacks: MutableList<Callback> = mutableListOf()

    fun addCallback(callback: Callback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback)
        }
    }

    fun removeCallback(callback: Callback) {
        callbacks.remove(callback)
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        super.log(priority, tag, message, t)

        callbacks.forEach { callback -> callback.onLog(LogMessage(priority, tag, message, t)) }
    }
}