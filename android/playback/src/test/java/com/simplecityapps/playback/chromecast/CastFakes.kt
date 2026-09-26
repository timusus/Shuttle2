package com.simplecityapps.playback.chromecast

import android.graphics.Bitmap
import android.net.Uri
import com.simplecityapps.imageloading.ArtworkImageLoader
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.SongPathRemap
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow

/** A library of [songs], changed only by [insert] and [update], which reports the songs it updated as the real repository does. */
class FakeSongRepository(private var songs: List<Song>) : SongRepository {
    /** Buffered, so an update made on the main thread doesn't wait there for a collector that runs on it too. */
    private val _updatedSongIds = MutableSharedFlow<Set<Long>>(extraBufferCapacity = 16)

    override val updatedSongIds: SharedFlow<Set<Long>> = _updatedSongIds.asSharedFlow()

    override fun getSongs(query: SongQuery): Flow<List<Song>?> = flow { emit(songs.filter(query.predicate)) }

    override suspend fun insert(
        songs: List<Song>,
        mediaProviderType: MediaProviderType
    ) {
        this.songs += songs
    }

    override suspend fun update(song: Song): Int = error("not called")

    override suspend fun update(songs: List<Song>) {
        val updates = songs.associateBy { song -> song.id }
        this.songs = this.songs.map { song -> updates[song.id] ?: song }
        _updatedSongIds.emit(updates.keys)
    }

    override suspend fun remove(song: Song) = error("not called")

    override suspend fun removeAll(mediaProviderType: MediaProviderType) = error("not called")

    override suspend fun insertUpdateAndDelete(
        inserts: List<Song>,
        updates: List<Song>,
        deletes: List<Song>,
        mediaProviderType: MediaProviderType
    ): Triple<Int, Int, Int> = error("not called")

    override suspend fun remapPaths(
        remaps: List<SongPathRemap>,
        mediaProviderType: MediaProviderType
    ): List<SongPathRemap> = error("not called")

    override suspend fun setPlaybackPosition(
        song: Song,
        playbackPosition: Int
    ) = error("not called")

    override suspend fun recordPlayedThrough(song: Song) = error("not called")

    override suspend fun setExcluded(
        songs: List<Song>,
        excluded: Boolean
    ) = error("not called")

    override suspend fun clearExcludeList() = error("not called")
}

/** Artwork that is [bytes] for every song. */
class FakeArtworkImageLoader(private val bytes: ByteArray?) : ArtworkImageLoader {
    override suspend fun loadBitmap(data: Any): ByteArray? = bytes

    override fun loadBitmap(
        data: Any,
        width: Int,
        height: Int,
        options: List<ArtworkImageLoader.Options>,
        onCompletion: (Bitmap?) -> Unit
    ): ArtworkImageLoader.Request = error("not called")

    override suspend fun clearCache() = error("not called")
}

/**
 * Streams a remote-provider song from `https://media.example/<id>?ApiKey=secret-token`, as audio/mpeg, or as
 * [transcodedType] when asked for a Cast-compatible stream; a local song plays its own file.
 */
class FakeMediaInfoProvider(private val transcodedType: String = TRANSCODED) : MediaInfoProvider {
    /** The songs asked for, with whether a Cast-compatible stream was wanted. */
    val requests = mutableListOf<Pair<Long, Boolean>>()

    /** While set, answers wait for it. */
    var gate: CompletableDeferred<Unit>? = null

    /** Songs whose stream fails to resolve. */
    val failing = mutableSetOf<Long>()

    override fun handles(scheme: String?): Boolean = true

    override suspend fun getMediaInfo(
        song: Song,
        castCompatibilityMode: Boolean
    ): MediaInfo {
        synchronized(requests) { requests += song.id to castCompatibilityMode }
        gate?.await()
        if (song.id in failing) throw IllegalStateException("No credentials")
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
        const val TRANSCODED = "application/x-mpegURL"

        fun remoteUrl(songId: Long) = "https://media.example/$songId?ApiKey=secret-token"
    }
}

/** A [testSong] from a Jellyfin server. */
fun remoteSong(id: Long) = testSong(id, path = "jellyfin://song/$id").copy(mediaProvider = MediaProviderType.Jellyfin)
