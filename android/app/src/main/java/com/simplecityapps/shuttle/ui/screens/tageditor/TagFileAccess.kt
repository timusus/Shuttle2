package com.simplecityapps.shuttle.ui.screens.tageditor

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.localmediaprovider.local.provider.taglib.FileScanner
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.shuttle.model.Song
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.FileNotFoundException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Reads and writes a song file's tags: the tag editor's only way to the file system. */
interface TagFileAccess {
    /** The tags in [song]'s file, or null if the file can't be read or isn't one the editor can write. */
    suspend fun read(song: Song): AudioFile?

    /**
     * Writes [metadata] (TagLib property keys to values) to [song]'s file.
     *
     * @return whether the write succeeded.
     */
    suspend fun write(
        song: Song,
        metadata: Map<String, List<String>>,
    ): Boolean
}

/** Tag access through the Storage Access Framework and KTagLib: only document URIs are editable. */
class SafTagFileAccess @Inject constructor(
    @ApplicationContext private val context: Context,
    private val kTagLib: KTagLib,
    private val fileScanner: FileScanner,
) : TagFileAccess {
    override suspend fun read(song: Song): AudioFile? {
        val uri = song.documentUri() ?: return null
        return fileScanner.getAudioFile(context, kTagLib, uri)
    }

    override suspend fun write(
        song: Song,
        metadata: Map<String, List<String>>,
    ): Boolean {
        val uri = song.documentUri() ?: return false
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                    kTagLib.writeMetadata(pfd.detachFd(), metadata, uri.lastPathSegment)
                } ?: false
            } catch (e: IllegalStateException) {
                Timber.e(e, "Failed to update tags")
                false
            } catch (e: FileNotFoundException) {
                Timber.e(e, "Failed to update tags")
                false
            } catch (e: SecurityException) {
                Timber.e(e, "Failed to update tags")
                false
            }
        }
    }

    private fun Song.documentUri(): Uri? {
        if (!path.startsWith("content://")) return null
        return Uri.parse(path).takeIf { DocumentsContract.isDocumentUri(context, it) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface TagFileAccessModule {
    @Binds
    fun bindTagFileAccess(access: SafTagFileAccess): TagFileAccess
}
