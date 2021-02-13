package com.simplecityapps.shuttle.debug

import android.content.Context
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import timber.log.Timber
import java.util.*

class DebugLoggingTree(
    private val context: Context,
    private val generalPreferenceManager: GeneralPreferenceManager
) : Timber.DebugTree() {

    interface Callback {
        fun onLog(logMessage: LogMessage)
    }

    var history = mutableListOf<LogMessage>()

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

        if (BuildConfig.DEBUG || generalPreferenceManager.debugFileLogging) {
            synchronized(this) {
                val logMessage = LogMessage(priority, tag, message, t)

                history.add(logMessage)

                callbacks.forEach { callback -> callback.onLog(logMessage) }

                if (generalPreferenceManager.debugFileLogging) {
                    writeToFile(context, logMessage)
                }
            }
        }
    }

    private fun writeToFile(context: Context, logMessage: LogMessage) {
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