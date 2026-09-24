package com.simplecityapps.playback.mediasession

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Resolves a content:// or file:// URI another app asked us to play to a [Song]: the library song for that
 * file when there is one (see [OpenedAudio.findIn]), otherwise a transient song that plays from the URI.
 *
 * A content:// URI from an ACTION_VIEW intent usually comes with a temporary read grant, which lasts while
 * the task that received it is alive (MainActivity is singleInstance, so its own task). That's enough to
 * play the file now; a library song doesn't depend on the grant at all, and a transient one isn't restored
 * after the app is killed. Persistable grants are deliberately not taken: they'd pile up for every file
 * opened, sharing a capped pool with the library folders picked in onboarding.
 */
class UriSongResolver(
    private val context: Context,
    private val songRepository: SongRepository
) {
    /** The song for [uri], or null when it can't be read (no grant, or the file is gone). */
    suspend fun resolve(
        uri: Uri,
        mimeType: String? = null
    ): Song? = withContext(Dispatchers.IO) {
        if (!canRead(uri)) return@withContext null
        val opened = inspect(uri, mimeType) ?: return@withContext null
        val librarySongs = songRepository.getSongs(SongQuery.All(includeExcluded = true)).filterNotNull().firstOrNull().orEmpty()
        opened.findIn(librarySongs) ?: opened.toTransientSong()
    }

    private fun canRead(uri: Uri): Boolean = try {
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> uri.path?.let { path -> File(path).canRead() } ?: false
            else -> context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        }
    } catch (e: Exception) {
        // SecurityException without a grant, FileNotFoundException when the file is gone
        Timber.w(e, "Can't read $uri")
        false
    }

    private fun inspect(
        uri: Uri,
        mimeType: String?
    ): OpenedAudio? = when (uri.scheme) {
        ContentResolver.SCHEME_FILE -> {
            val path = uri.path ?: return null
            OpenedAudio(uri = uri.toString(), filePath = path, displayName = uri.lastPathSegment, mimeType = mimeType)
                .withEmbeddedMetadata(uri)
        }

        ContentResolver.SCHEME_CONTENT -> inspectContent(uri, mimeType)

        else -> null
    }

    private fun inspectContent(
        uri: Uri,
        mimeType: String?
    ): OpenedAudio {
        var displayName: String? = null
        var size: Long? = null
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    displayName = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it != -1 }?.let(cursor::getString)
                    size = cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it != -1 && !cursor.isNull(it) }?.let(cursor::getLong)
                }
            }
        } catch (e: Exception) {
            // Some providers reject the projection; the name and size are only a fallback for missing tags
            Timber.w(e, "Failed to query $uri")
        }

        val documentId = if (DocumentsContract.isDocumentUri(context, uri)) DocumentsContract.getDocumentId(uri) else null

        return OpenedAudio(
            uri = uri.toString(),
            filePath = filePathFor(uri, documentId),
            documentId = documentId,
            authority = uri.authority,
            displayName = displayName,
            mimeType = context.contentResolver.getType(uri) ?: mimeType,
            size = size
        ).withEmbeddedMetadata(uri)
    }

    /** The file path behind a MediaStore or external storage URI, so a MediaStore library song can match it. */
    @Suppress("DEPRECATION") // MediaStore.MediaColumns.DATA is still populated for audio the app can read
    private fun filePathFor(
        uri: Uri,
        documentId: String?
    ): String? {
        if (documentId != null && uri.authority == EXTERNAL_STORAGE_AUTHORITY) {
            val (volume, relativePath) = documentId.split(':', limit = 2).takeIf { it.size == 2 } ?: return null
            return if (volume == "primary") "${Environment.getExternalStorageDirectory()}/$relativePath" else "/storage/$volume/$relativePath"
        }
        val mediaUri =
            when {
                uri.authority == MediaStore.AUTHORITY -> uri

                documentId != null && uri.authority == MEDIA_DOCUMENTS_AUTHORITY && documentId.startsWith("audio:") ->
                    documentId.removePrefix("audio:").toLongOrNull()?.let { id -> ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id) }

                else -> null
            } ?: return null
        return try {
            context.contentResolver.query(mediaUri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to query the file path for $uri")
            null
        }
    }

    private fun OpenedAudio.withEmbeddedMetadata(uri: Uri): OpenedAudio {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            copy(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull()
            )
        } catch (e: Exception) {
            // Tags are a nicety; ExoPlayer may still play what the retriever can't parse
            Timber.w(e, "Failed to read metadata from $uri")
            this
        } finally {
            retriever.release()
        }
    }

    private companion object {
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        const val MEDIA_DOCUMENTS_AUTHORITY = "com.android.providers.media.documents"
    }
}
