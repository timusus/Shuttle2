package com.simplecityapps.taglib

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class FileScanner {

    private external fun getAudioFile(uri: String, fd: Int, name: String): AudioFile?

    fun getAudioFile(context: Context, uri: Uri): AudioFile? {
        val documentFile = DocumentFile.fromSingleUri(context, uri)
        context.contentResolver.openFileDescriptor(documentFile!!.uri, "r")?.use { pfd ->
            return getAudioFile(uri.toString(), pfd.fd, documentFile.name ?: "Unknown")
        }

        return null
    }

    companion object {

        init {
            System.loadLibrary("file-scanner")
        }
    }
}