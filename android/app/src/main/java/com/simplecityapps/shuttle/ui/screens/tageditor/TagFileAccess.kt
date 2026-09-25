package com.simplecityapps.shuttle.ui.screens.tageditor

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.localmediaprovider.local.provider.getAudioFile
import com.simplecityapps.localmediaprovider.local.provider.taglib.FileScanner
import com.simplecityapps.localmediaprovider.local.provider.taglib.externalStorageTreeFolder
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.shuttle.model.Song
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Reads and writes a song file's tags: the tag editor's only way to the file system. */
interface TagFileAccess {
    /** The tags in [song]'s file, or null if the file can't be read or isn't one the editor can write. */
    suspend fun read(song: Song): AudioFile?

    /**
     * The system's request for the user's consent to change [songs]' files, or null when S2 can already write them all.
     * Launch it before [write]; declined, the writes fail.
     */
    suspend fun writeConsent(songs: List<Song>): IntentSender?

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

/**
 * Tag access through KTagLib, by the first route that can write the file (#406):
 * - a song read through a folder grant (Settings > Sources) is a document URI, written through the grant;
 * - a file under a granted folder is written through that grant, as its document;
 * - otherwise, on Android 11 and up, the file's MediaStore entry, once the user consents ([writeConsent]).
 *
 * Below Android 11 a file outside every granted folder can't be written, so it can't be edited either.
 */
class DeviceTagFileAccess @Inject constructor(
    @ApplicationContext private val context: Context,
    private val kTagLib: KTagLib,
    private val fileScanner: FileScanner,
) : TagFileAccess {
    override suspend fun read(song: Song): AudioFile? = when (val target = target(song)) {
        is TagTarget.Document -> fileScanner.getAudioFile(context, kTagLib, target.uri)
        is TagTarget.Media -> readMedia(song, target.uri)
        null -> null
    }

    override suspend fun writeConsent(songs: List<Song>): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val uris = songs.mapNotNull { song -> (target(song) as? TagTarget.Media)?.uri }
        if (uris.isEmpty()) return null
        return MediaStore.createWriteRequest(context.contentResolver, uris).intentSender
    }

    override suspend fun write(
        song: Song,
        metadata: Map<String, List<String>>,
    ): Boolean {
        val target = target(song) ?: return false
        // KTagLib picks the tag format by the name's extension; a MediaStore URI's last segment is only its id
        val fileName = if (target is TagTarget.Media) File(song.path).name else target.uri.lastPathSegment
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openFileDescriptor(target.uri, "rw")?.use { pfd ->
                    kTagLib.writeMetadata(pfd.detachFd(), metadata, fileName)
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

    private sealed interface TagTarget {
        val uri: Uri

        data class Document(override val uri: Uri) : TagTarget

        data class Media(override val uri: Uri) : TagTarget
    }

    private suspend fun target(song: Song): TagTarget? = withContext(Dispatchers.IO) {
        if (song.path.startsWith("content://")) {
            return@withContext Uri.parse(song.path).takeIf { DocumentsContract.isDocumentUri(context, it) }?.let { TagTarget.Document(it) }
        }
        grantedDocumentUri(song.path)?.let { return@withContext TagTarget.Document(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) mediaUri(song.path)?.let { TagTarget.Media(it) } else null
    }

    /** [path] as a document under a folder S2 holds a write grant for, if any. */
    private fun grantedDocumentUri(path: String): Uri? {
        val primaryStoragePath = Environment.getExternalStorageDirectory().path
        return context.contentResolver.persistedUriPermissions
            .filter { it.isWritePermission }
            .firstNotNullOfOrNull { permission ->
                val treeUri = permission.uri
                val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return@firstNotNullOfOrNull null
                val treePath = externalStorageTreeFolder(treeUri.authority, treeDocumentId, primaryStoragePath) ?: return@firstNotNullOfOrNull null
                documentIdForPath(path, treeDocumentId, treePath)?.let { DocumentsContract.buildDocumentUriUsingTree(treeUri, it) }
            }
    }

    private fun mediaUri(path: String): Uri? = try {
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.DATA} = ?",
            arrayOf(path),
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0)) else null }
    } catch (e: SecurityException) {
        Timber.e(e, "Failed to find the MediaStore entry for $path")
        null
    }

    private suspend fun readMedia(
        song: Song,
        uri: Uri,
    ): AudioFile? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                kTagLib.getAudioFile(pfd.detachFd(), song.path, File(song.path).name, song.lastModified?.toEpochMilliseconds() ?: 0L, song.size, song.mimeType)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The native tag parse can throw anything for a corrupt file
            Timber.e(e, "Failed to read tags: $uri (${song.path})")
            null
        }
    }
}

/**
 * The document id of the file at [path] inside the tree whose root document is [treeDocumentId], found at [treePath];
 * null if the file isn't in that tree. Document ids of the external storage provider are `<root>:<relative path>`.
 */
internal fun documentIdForPath(
    path: String,
    treeDocumentId: String,
    treePath: String,
): String? {
    val folder = treePath.trimEnd('/')
    if (!path.startsWith("$folder/")) return null
    val relative = path.removePrefix("$folder/")
    return if (treeDocumentId.endsWith(':')) treeDocumentId + relative else "${treeDocumentId.trimEnd('/')}/$relative"
}

@Module
@InstallIn(SingletonComponent::class)
interface TagFileAccessModule {
    @Binds
    fun bindTagFileAccess(access: DeviceTagFileAccess): TagFileAccess
}
