package com.simplecityapps.shuttle.debug

import android.content.Context
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.debug.livelog.LiveLogSink
import com.simplecityapps.shuttle.settings.DebugSettings
import java.util.*
import timber.log.Timber

class DebugLoggingTree(
    private val context: Context,
    private val debugSettings: DebugSettings,
    private val liveLogSink: Optional<LiveLogSink> = Optional.empty()
) : Timber.DebugTree() {
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?
    ) {
        if (BuildConfig.DEBUG) {
            super.log(priority, tag, message, t)
        }
        liveLogSink.ifPresent { it.log(priority, tag, message, t) }
        if (debugSettings.fileLogging.value) {
            synchronized(this) {
                writeToFile(context, LogMessage(priority, tag, message, t))
            }
        }
    }

    private fun writeToFile(
        context: Context,
        logMessage: LogMessage
    ) {
        val file = context.getFileStreamPath(FILE_NAME)

        // If the file is more than 24 hours old, or larger than 512kB, delete it
        if (file.exists() && (((Date().time - file.lastModified()) > 24 * 60 * 60 * 1000) || file.length() > 512 * 1024)) {
            file.delete()
        }

        context.openFileOutput(FILE_NAME, Context.MODE_APPEND).use { outputStream ->
            outputStream.write((logMessage.toString() + "\n\n").toByteArray())
        }
    }

    companion object {
        const val FILE_NAME = "logs.txt"
    }
}
