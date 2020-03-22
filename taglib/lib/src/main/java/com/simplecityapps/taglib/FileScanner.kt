package com.simplecityapps.taglib

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import timber.log.Timber
import java.util.*

class FileScanner {

    private external fun getAudioFile(uri: String, fd: Int, name: String): AudioFile?

    fun getAudioFile(context: Context, uri: Uri): AudioFile? {
        DocumentFile.fromSingleUri(context, uri)?.let { documentFile ->
            try {
                context.contentResolver.openFileDescriptor(documentFile.uri, "r")?.use { pfd ->
                    val audioFile = getAudioFile(uri.toString(), pfd.fd, documentFile.name?.substringBeforeLast(".") ?: "Unknown")
                    if (audioFile != null) {
                        return audioFile
                    } else {
                        Calendar.getInstance().time

                        return AudioFile(
                            name = documentFile.name?.substringBeforeLast(".") ?: "Unknown",
                            albumArtistName = "Unknown",
                            artistName = "Unknown",
                            albumName = documentFile.parentFile?.name ?: "Unknown",
                            track = 1,
                            disc = 1,
                            duration = 0,
                            year = Calendar.getInstance().apply { time = Date(documentFile.lastModified()) }.get(Calendar.YEAR),
                            path = uri.toString(),
                            size = documentFile.length(),
                            lastModified = documentFile.lastModified()
                        )
                    }
                }
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Failed to retrieve audio file for uri: $uri")
            }
        }

        return null
    }

    companion object {
        init {
            System.loadLibrary("file-scanner")
        }
    }
}
