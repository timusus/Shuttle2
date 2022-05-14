package com.simplecityapps.shuttle.debug

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.*

class LogMessage(val priority: Int, val tag: String?, val message: String, val throwable: Throwable?) {
    val date: Date = Date()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LogMessage

        if (priority != other.priority) return false
        if (tag != other.tag) return false
        if (message != other.message) return false

        return true
    }

    override fun hashCode(): Int {
        var result = priority
        result = 31 * result + (tag?.hashCode() ?: 0)
        result = 31 * result + message.hashCode()
        return result
    }

    override fun toString(): String {
        return "${dateFormat.format(date)}" +
            "\n$priority/$tag" +
            "\n$message"
    }

    companion object {
        @SuppressLint("SimpleDateFormat")
        val dateFormat = SimpleDateFormat("hh:mm:ss.SSS")
    }
}
