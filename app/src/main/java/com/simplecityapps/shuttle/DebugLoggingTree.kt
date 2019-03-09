package com.simplecityapps.shuttle

import android.text.SpannableString
import android.util.Log
import androidx.annotation.ColorInt
import com.simplecityapps.shuttle.ui.common.bold
import com.simplecityapps.shuttle.ui.common.color
import com.simplecityapps.shuttle.ui.common.plus
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

class DebugLoggingTree : Timber.DebugTree() {

    interface Callbacks {
        fun onTextChanged(text: SpannableString)
    }

    var callback: Callbacks? = null

    private var text: SpannableString = SpannableString("")

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        super.log(priority, tag, message, t)

        text += SpannableString(dateFormat.format(Date())) + "\n"
        text += bold(color(priority.getColor(), priority.getPriorityString()))+ "/"
        text += bold(tag ?: "") + "\n> "
        text += SpannableString(message)
        text += "\n\n"

        callback?.onTextChanged(text)
    }

    private fun Int.getPriorityString(): String {
        return when (this) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            else -> ""
        }
    }

    @ColorInt
    private fun Int.getColor(): Int {
        return when (this) {
            Log.VERBOSE -> 0XFF03a9f4.toInt()
            Log.DEBUG -> 0XFF03a9f4.toInt()
            Log.INFO -> 0XFF1a237e.toInt()
            Log.WARN -> 0XFFef6c00.toInt()
            Log.ERROR -> 0xFFf44336.toInt()
            else -> 0XFF000000.toInt()
        }
    }

    companion object {
        val dateFormat = SimpleDateFormat("hh:mm:ss")
    }
}