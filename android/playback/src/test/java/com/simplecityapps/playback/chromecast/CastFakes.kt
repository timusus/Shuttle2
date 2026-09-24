package com.simplecityapps.playback.chromecast

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.ImageView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.palette.ColorSet
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** A library of [songs], read only. */
class FakeSongRepository(private val songs: List<Song>) : SongRepository {
    override fun getSongs(query: SongQuery): Flow<List<Song>?> = flowOf(songs.filter(query.predicate))

    override suspend fun insert(
        songs: List<Song>,
        mediaProviderType: MediaProviderType
    ) = error("not called")

    override suspend fun update(song: Song): Int = error("not called")

    override suspend fun update(songs: List<Song>) = error("not called")

    override suspend fun remove(song: Song) = error("not called")

    override suspend fun removeAll(mediaProviderType: MediaProviderType) = error("not called")

    override suspend fun insertUpdateAndDelete(
        inserts: List<Song>,
        updates: List<Song>,
        deletes: List<Song>,
        mediaProviderType: MediaProviderType
    ): Triple<Int, Int, Int> = error("not called")

    override suspend fun incrementPlayCount(song: Song) = error("not called")

    override suspend fun setPlaybackPosition(
        song: Song,
        playbackPosition: Int
    ) = error("not called")

    override suspend fun setExcluded(
        songs: List<Song>,
        excluded: Boolean
    ) = error("not called")

    override suspend fun clearExcludeList() = error("not called")
}

/** Artwork that is [bytes] for every song. */
class FakeArtworkImageLoader(private val bytes: ByteArray?) : ArtworkImageLoader {
    override fun loadBitmap(data: Any): ByteArray? = bytes

    override fun loadArtwork(
        imageView: ImageView,
        data: Any,
        options: List<ArtworkImageLoader.Options>,
        onCompletion: ((Result<Unit>) -> Unit)?,
        onColorSetGenerated: ((ColorSet) -> Unit)?
    ) = error("not called")

    override fun loadBitmap(
        data: Any,
        width: Int,
        height: Int,
        options: List<ArtworkImageLoader.Options>,
        onCompletion: (Bitmap?) -> Unit
    ): ArtworkImageLoader.Request = error("not called")

    override fun loadColorSet(
        data: Any,
        callback: (ColorSet?) -> Unit
    ) = error("not called")

    override fun clear(imageView: ImageView) = error("not called")

    override suspend fun clearCache(context: Context?) = error("not called")
}

/**
 * Streams a remote-provider song from `https://media.example/<id>?ApiKey=secret-token`, as audio/mpeg, or as
 * [transcodedType] when asked for a Cast-compatible stream; a local song plays its own file.
 */
class FakeMediaInfoProvider(private val transcodedType: String = "application/x-mpegURL") : MediaInfoProvider {
    /** The songs asked for, with whether a Cast-compatible stream was wanted. */
    val requests = mutableListOf<Pair<Long, Boolean>>()

    override fun handles(uri: Uri): Boolean = true

    override suspend fun getMediaInfo(
        song: Song,
        castCompatibilityMode: Boolean
    ): MediaInfo {
        synchronized(requests) { requests += song.id to castCompatibilityMode }
        return if (song.mediaProvider.remote) {
            MediaInfo(Uri.parse(remoteUrl(song.id)), if (castCompatibilityMode) transcodedType else "audio/mpeg", isRemote = true)
        } else {
            MediaInfo(Uri.parse(song.path), song.mimeType, isRemote = false)
        }
    }

    override suspend fun downloadUri(song: Song): Uri? = error("not called")

    override suspend fun downloadFallbackUri(
        path: String,
        responseCode: Int
    ): Uri? = error("not called")

    companion object {
        fun remoteUrl(songId: Long) = "https://media.example/$songId?ApiKey=secret-token"
    }
}
