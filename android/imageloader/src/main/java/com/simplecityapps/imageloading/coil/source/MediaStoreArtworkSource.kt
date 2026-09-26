package com.simplecityapps.imageloading.coil.source

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.simplecityapps.imageloading.coil.ArtworkSource
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import java.io.InputStream

/**
 * MediaStore's audio thumbnail for a MediaStore song (see [openMediaStoreAudioThumbnail]). Registered on Android 13+, where the
 * app can't list folder images, after the embedded source, which reads embedded art at full size.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class MediaStoreSongArtworkSource(
    private val context: Context
) : ArtworkSource.Local<Song> {
    override fun handles(model: Song): Boolean = model.mediaStoreId() != null

    override suspend fun open(model: Song): InputStream? = model.mediaStoreId()?.let { mediaStoreId -> context.contentResolver.openMediaStoreAudioThumbnail(mediaStoreId) }
}

/** Folder art for a MediaStore album on Android 13+, via the audio thumbnail of the album's first MediaStore song. */
@RequiresApi(Build.VERSION_CODES.Q)
internal class MediaStoreAlbumArtworkSource(
    private val context: Context,
    private val songRepository: SongRepository
) : ArtworkSource.Local<Album> {
    override fun handles(model: Album): Boolean = MediaProviderType.MediaStore in model.mediaProviders

    override suspend fun open(model: Album): InputStream? = songRepository.songsOf(model)
        .firstNotNullOfOrNull { song -> song.mediaStoreId() }
        ?.let { mediaStoreId -> context.contentResolver.openMediaStoreAudioThumbnail(mediaStoreId) }
}
