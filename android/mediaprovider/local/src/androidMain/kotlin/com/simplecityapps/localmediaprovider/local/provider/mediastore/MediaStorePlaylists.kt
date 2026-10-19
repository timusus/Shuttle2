package com.simplecityapps.localmediaprovider.local.provider.mediastore

import android.content.Context
import android.provider.MediaStore
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.ImportPhase
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.PlaylistListing
import com.simplecityapps.mediaprovider.PlaylistUpdateData
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.splitArtistTag
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import kotlin.math.abs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

internal data class MediaStoreSong(
    val playOrder: Long,
    val title: String?,
    val album: String?,
    val artist: String?,
    val albumArtist: String?,
    val duration: Int,
    val year: Int?,
    val track: Int,
    val mimeType: String,
    val path: String
)

internal data class MediaStorePlaylist(val id: Long, val name: String)

/** The playlists MediaStore holds, matched to the stored songs. */
internal class MediaStorePlaylists(
    private val context: Context,
    private val type: MediaProviderType,
    private val ioDispatcher: CoroutineDispatcher
) {
    fun findPlaylists(existingSongs: List<Song>): Flow<FlowEvent<PlaylistListing, MessageProgress>> = flow {
        val mediaStorePlaylists = findMediaStorePlaylists().toList()
        val updates =
            mediaStorePlaylists.mapIndexed { i, mediaStorePlaylist ->
                val mediaStoreSongs = findSongsForMediaStorePlaylist(mediaStorePlaylist.id)

                // Associate Media Store songs with Shuttle's songs
                val matchingSongs =
                    mediaStoreSongs.mapNotNull { mediaStoreSong ->
                        existingSongs.firstOrNull { existingSong -> existingSong.matchesPlaylistEntry(mediaStoreSong) }
                    }

                val updateData =
                    PlaylistUpdateData(
                        type,
                        mediaStorePlaylist.name,
                        matchingSongs,
                        mediaStorePlaylist.id.toString()
                    )
                emit(FlowEvent.Progress(MessageProgress(ImportPhase.Fetching, Progress(i, mediaStorePlaylists.size))))
                updateData
            }

        emit(FlowEvent.Success(PlaylistListing(updates.toList())))
    }

    private suspend fun findSongsForMediaStorePlaylist(mediaStorePlaylistId: Long): List<MediaStoreSong> {
        return withContext(ioDispatcher) {
            val songs = mutableListOf<MediaStoreSong>()

            val cursor =
                context.contentResolver.query(
                    MediaStore.Audio.Playlists.Members.getContentUri("external", mediaStorePlaylistId),
                    arrayOf(
                        MediaStore.Audio.Playlists.Members.TITLE,
                        MediaStore.Audio.Playlists.Members.ALBUM,
                        MediaStore.Audio.Playlists.Members.ARTIST,
                        MediaStore.Audio.Playlists.Members.DURATION,
                        MediaStore.Audio.Playlists.Members.YEAR,
                        MediaStore.Audio.Media.TRACK,
                        MediaStore.Audio.Playlists.Members.MIME_TYPE,
                        MediaStore.Audio.Playlists.Members.DATA,
                        MediaStore.Audio.Playlists.Members.PLAY_ORDER,
                        "album_artist"
                    ),
                    null,
                    null,
                    MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER
                )

            cursor?.use {
                while (cursor.moveToNext()) {
                    if (!isActive) {
                        return@use
                    }

                    val rawTrack =
                        cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK))
                    val track = decodeDiscTrack(rawTrack, discNumberColumnValue = null).track

                    songs.add(
                        MediaStoreSong(
                            playOrder = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.PLAY_ORDER)),
                            title = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.TITLE)),
                            album = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.ALBUM)),
                            artist = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)),
                            albumArtist = cursor.getStringOrNull(cursor.getColumnIndex("album_artist")),
                            duration = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.DURATION)),
                            year = cursor.getIntOrNull(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.YEAR)),
                            track = track,
                            mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.MIME_TYPE)),
                            path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.DATA))
                        )
                    )
                }
            }

            songs
        }
    }

    private fun findMediaStorePlaylists(): Flow<MediaStorePlaylist> = flow {
        val cursor =
            context.contentResolver.query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.Playlists._ID,
                    MediaStore.Audio.Playlists.NAME
                ),
                null,
                null,
                MediaStore.Audio.Playlists.DEFAULT_SORT_ORDER
            )

        cursor?.use {
            while (cursor.moveToNext()) {
                emit(
                    MediaStorePlaylist(
                        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME))
                    )
                )
            }
        }
    }
}

// MediaStore's scanner reads some tags differently from ours, so match loosely: title, album, duration, and any shared artist.
internal fun Song.matchesPlaylistEntry(mediaStoreSong: MediaStoreSong): Boolean {
    val mediaStoreArtists = mediaStoreSong.artist?.let(::splitArtistTag).orEmpty()
    val songArtists = artists.flatMap(::splitArtistTag)
    return name.equals(mediaStoreSong.title, ignoreCase = true) &&
        album.equals(mediaStoreSong.album, ignoreCase = true) &&
        (songArtists.any { artist -> mediaStoreArtists.any { it.equals(artist, ignoreCase = true) } } || albumArtist.equals(mediaStoreSong.albumArtist, ignoreCase = true)) &&
        abs(duration - mediaStoreSong.duration) <= 1000 // song duration is within 1 second
}
