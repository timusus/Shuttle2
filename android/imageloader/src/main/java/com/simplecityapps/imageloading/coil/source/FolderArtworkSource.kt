package com.simplecityapps.imageloading.coil.source

import android.content.Context
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.simplecityapps.imageloading.coil.ArtworkSource
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import java.io.File
import java.io.InputStream
import java.util.regex.Pattern

/** A cover image (folder.jpg, cover.png, ...) in the song's folder. */
internal class FolderSongArtworkSource(
    private val context: Context,
    private val sharedStorageListsImages: Boolean
) : ArtworkSource.Local<Song> {
    override fun handles(model: Song): Boolean = canListFolderImages(listOf(model.mediaProvider), sharedStorageListsImages)

    override suspend fun open(model: Song): InputStream? = context.openFolderCover(model)
}

/** A cover image in the folder of the album's first song. */
internal class FolderAlbumArtworkSource(
    private val context: Context,
    private val songRepository: SongRepository,
    private val sharedStorageListsImages: Boolean
) : ArtworkSource.Local<Album> {
    override fun handles(model: Album): Boolean = canListFolderImages(model.mediaProviders, sharedStorageListsImages)

    override suspend fun open(model: Album): InputStream? = songRepository.firstSongOf(model)?.let { song -> context.openFolderCover(song) }
}

/**
 * An artist image (artist.jpg, or a file named after the artist) in the folder of the artist's first song or the folder above
 * it, where artist folders usually sit.
 */
internal class FolderAlbumArtistArtworkSource(
    private val context: Context,
    private val songRepository: SongRepository,
    private val sharedStorageListsImages: Boolean
) : ArtworkSource.Local<AlbumArtist> {
    override fun handles(model: AlbumArtist): Boolean = canListFolderImages(model.mediaProviders, sharedStorageListsImages)

    override suspend fun open(model: AlbumArtist): InputStream? {
        val song = songRepository.firstSongOf(model) ?: return null
        val artistName = model.friendlyArtistName ?: model.name ?: ""
        val folders =
            if (DocumentsContract.isDocumentUri(context, song.path.toUri())) {
                val parent = song.path.substringBeforeLast("%2F", "")
                val grandParent = parent.substringBeforeLast("%2F", "")
                listOf(parent, grandParent)
                    .filter { it.isNotEmpty() }
                    .mapNotNull { DocumentFile.fromTreeUri(context, it.toUri()) }
            } else {
                val parent = File(song.path).parentFile
                listOfNotNull(parent, parent?.parentFile).map { DocumentFile.fromFile(it) }
            }
        return context.openLargestImage(folders) { name -> artistPattern.matcher(name).matches() || artistName.contains(name, ignoreCase = true) }
    }
}

private val coverPattern by lazy { Pattern.compile("(\\.?(folder|cover|album|albumart|front|artwork)).*\\.(jpg|jpeg|png|webp)", Pattern.CASE_INSENSITIVE) }

private val artistPattern by lazy { Pattern.compile("(\\.?)artist.*\\.(jpg|jpeg|png|webp)", Pattern.CASE_INSENSITIVE) }

private fun Context.openFolderCover(song: Song): InputStream? {
    val folder =
        if (DocumentsContract.isDocumentUri(this, song.path.toUri())) {
            song.path.substringBeforeLast("%2F", "")
                .takeIf { it.isNotEmpty() }
                ?.let { parent -> DocumentFile.fromTreeUri(this, parent.toUri()) }
        } else {
            File(song.path).parentFile?.let { parent -> DocumentFile.fromFile(parent) }
        }
    return openLargestImage(listOfNotNull(folder)) { name -> coverPattern.matcher(name).matches() }
}

/** Opens the largest image over 1 KB in [folders] whose name matches, skipping tiny thumbnails and placeholders. */
private fun Context.openLargestImage(
    folders: List<DocumentFile>,
    nameMatches: (String) -> Boolean
): InputStream? = folders
    .flatMap { folder -> folder.listFiles().toList() }
    .filter { file -> file.type?.startsWith("image") == true && file.length() > 1024 && nameMatches(file.name ?: "") }
    .maxByOrNull { file -> file.length() }
    ?.let { file -> contentResolver.openInputStream(file.uri) }
