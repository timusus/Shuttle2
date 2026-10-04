package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.localmediaprovider.local.provider.getAudioFile
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.saf.DocumentNode
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

open class FileScanner {
    /** Reads a file the SAF tree walk found, using the name, mime type, size and modified date the walk already returned. */
    open suspend fun getAudioFile(
        context: Context,
        kTagLib: KTagLib,
        node: DocumentNode
    ): AudioFile? = read(context, kTagLib, node.uri, node.displayName, node.lastModified, node.size, node.mimeType)

    /** Reads a document by [uri] alone, which costs a query for each of its attributes. */
    suspend fun getAudioFile(
        context: Context,
        kTagLib: KTagLib,
        uri: Uri
    ): AudioFile? = withContext(Dispatchers.IO) {
        val documentFile = DocumentFile.fromSingleUri(context, uri)
        if (documentFile?.exists() == true) {
            read(context, kTagLib, uri, documentFile.name ?: "Unknown", documentFile.lastModified(), documentFile.length(), documentFile.type)
        } else {
            Timber.e("Document file doesn't exist for uri: $uri")
            null
        }
    }

    private suspend fun read(
        context: Context,
        kTagLib: KTagLib,
        uri: Uri,
        name: String,
        lastModified: Long,
        size: Long,
        mimeType: String?
    ): AudioFile? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                kTagLib.getAudioFile(pfd.detachFd(), uri.toString(), name, lastModified, size, mimeType)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The native tag parse can throw anything for a corrupt file; one bad file shouldn't fail the whole import
            Timber.e(e, "Failed to retrieve audio file for uri: $uri")
            null
        }
    }
}
