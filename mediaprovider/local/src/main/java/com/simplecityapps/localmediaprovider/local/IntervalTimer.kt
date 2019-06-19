package com.simplecityapps.localmediaprovider.local

import android.util.Log

class IntervalTimer {

    private var initialTime: Long = 0
    private var intervalTime: Long = 0

    /**
     * Call to begin tracking time intervals. Subsequent calls to [.logInterval] will
     * output the time since this call.
     */
    fun startLog() {
        initialTime = System.currentTimeMillis()
        intervalTime = System.currentTimeMillis()
    }

    fun getInterval(): Long {
        val time = System.currentTimeMillis() - intervalTime
        intervalTime = System.currentTimeMillis()
        return time
    }

    fun getTotal(): Long {
        return System.currentTimeMillis() - initialTime
    }

    /**
     * Log the time since the last logInterval() was called.
     *
     *
     * Note: Must call startLog() or the 'total' time won't be accurate.
     *
     *
     *
     * @param tag     the tag to use for the log message
     * @param message the message to output
     */
    fun logInterval(tag: String, message: String) {
        if (initialTime <= 0) {
            Log.e(tag, "Must call startLog() or the 'total' time won't be accurate")
        }
        Log.i(tag, "$message \nInterval: ${getInterval()} \nTotal: ${getTotal()}")
    }
}