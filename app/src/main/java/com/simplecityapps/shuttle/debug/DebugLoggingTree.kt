package com.simplecityapps.shuttle.debug

import timber.log.Timber

class DebugLoggingTree : Timber.DebugTree() {

    interface Callbacks {
        fun onLog(logMessage: LogMessage)
    }

    var callback: Callbacks? = null

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        super.log(priority, tag, message, t)

        callback?.onLog(LogMessage(priority, tag, message, t))
    }
}