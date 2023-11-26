package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.localmediaprovider.local.provider.getAudioFile
import com.simplecityapps.mediaprovider.model.AudioFile
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class FileScanner {
    suspend fun getAudioFile(
        context: Context,
        kTagLib: KTagLib,
        uri: Uri
    ): AudioFile? {
        return withContext(Dispatchers.IO) {
            DocumentFile.fromSingleUri(context, uri)?.let { documentFile ->
                if (documentFile.exists()) {
                    try {
                        context.contentResolver.openFileDescriptor(documentFile.uri, "r")?.use { pfd ->
                            return@withContext kTagLib.getAudioFile(
                                pfd.detachFd(),
                                uri.toString(),
                                documentFile.name?.substringBeforeLast(".") ?: "Unknown",
                                documentFile.lastModified(),
                                documentFile.length(),
                                documentFile.type
                            )
                        }
                    } catch (e: IllegalArgumentException) {
                        Timber.e(e, "Failed to retrieve audio file for uri: $uri")
                    } catch (e: FileNotFoundException) {
                        Timber.e(e, "Failed to retrieve audio file for uri: $uri")
                    } catch (e: IllegalStateException) {
                        Timber.e(e, "Failed to retrieve audio file for uri: $uri")
                    } catch (e: SecurityException) {
                        Timber.e(e, "Failed to retrieve audio file for uri: $uri")
                    }
                } else {
                    Timber.e("Document file doesn't exist for uri: $uri")
                }
            }
            null
        }
    }
}
