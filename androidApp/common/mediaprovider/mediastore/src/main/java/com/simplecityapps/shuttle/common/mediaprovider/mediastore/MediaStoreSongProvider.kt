package com.simplecityapps.shuttle.common.mediaprovider.mediastore

import android.content.ContentResolver
import android.content.Context
import android.provider.MediaStore
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import com.simplecityapps.shuttle.common.mediaprovider.mediastore.di.MediaStorePlaylist
import com.simplecityapps.shuttle.mediaprovider.common.MediaProvider
import com.simplecityapps.shuttle.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import kotlinx.datetime.LocalDate
import kotlin.math.abs

class MediaStoreMediaProvider(
    private val context: Context
) : MediaProvider {

    override val type: MediaProviderType = MediaProviderType.MediaStore

    override fun findSongs(): Flow<MediaProvider.SongRetrievalState> {
        return flow {
            emit(MediaProvider.SongRetrievalState.QueryingDatabase(null))
            val songData = updateGenreData(context.contentResolver, getSongData(context.contentResolver))
            emit(MediaProvider.SongRetrievalState.Complete(songData))
        }
    }

    override fun findPlaylists(existingSongs: List<Song>): Flow<MediaProvider.PlaylistRetrievalState> {
        return flow {
            emit(MediaProvider.PlaylistRetrievalState.QueryingDatabase(null))
            val mediaStorePlaylists = findMediaStorePlaylists(context.contentResolver)
            val playlists = mediaStorePlaylists.mapIndexed { index, mediaStorePlaylist ->
                val mediaStoreSongs = findSongsForMediaStorePlaylist(context.contentResolver, mediaStorePlaylist.id)

                // Associate Media Store songs with Shuttle's songs
                val matchingSongs = mediaStoreSongs.mapNotNull { mediaStoreSong ->
                    existingSongs.firstOrNull { existingSong ->
                        // We assume two songs are equal, if they have the same title, album, artist & duration. We can't be too specific, as the
                        // MediaStore scanner may have interpreted some fields differently to Shuttle's built in scanner.
                        existingSong.name.equals(mediaStoreSong.title, ignoreCase = true)
                                && existingSong.album.equals(mediaStoreSong.album, ignoreCase = true)
                                && (existingSong.artists.any { it.equals(mediaStoreSong.artist, true) } || existingSong.albumArtist.equals(mediaStoreSong.albumArtist, ignoreCase = true))
                                && abs((existingSong.duration ?: 0) - mediaStoreSong.duration) <= 1000 // song duration is within 1 second
                    }
                }

                val playlist = PlaylistData(mediaStorePlaylist.name, type, mediaStorePlaylist.id.toString(), matchingSongs)
                emit(MediaProvider.PlaylistRetrievalState.QueryingDatabase(Progress(index, mediaStorePlaylists.size)))
                playlist
            }
            emit(MediaProvider.PlaylistRetrievalState.Complete(playlists))
        }
    }


    // Songs

    /**
     * Queries the MediaStore for song information.
     * Note: Genre data is not yet populated
     */
    private suspend fun getSongData(contentResolver: ContentResolver): List<SongData> {
        return withContext(Dispatchers.IO) {
            val songs = mutableListOf<SongData>()

            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST_ID,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.YEAR,
                    MediaStore.Audio.Media.TRACK,
                    MediaStore.Audio.Media.DATE_MODIFIED,
                    MediaStore.Audio.Media.IS_PODCAST,
                    MediaStore.Audio.Media.BOOKMARK,
                    MediaStore.Audio.Media.MIME_TYPE,
                    "album_artist"
                ),
                "${MediaStore.Audio.Media.IS_MUSIC}=1 OR ${MediaStore.Audio.Media.IS_PODCAST}=1",
                null,
                null
            )?.use { cursor ->
                val trackIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumArtistIndex = cursor.getColumnIndex("album_artist")
                val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val yearIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dateModifierIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)

                while (isActive && cursor.moveToNext()) {
                    var track = cursor.getInt(trackIndex)
                    var disc = 1
                    if (track >= 1000) {
                        track %= 1000

                        disc = track / 1000
                        if (disc == 0) {
                            disc = 1
                        }
                    }

                    songs.add(
                        SongData(
                            name = cursor.getStringOrNull(titleIndex),
                            artists = cursor.getStringOrNull(artistIndex)?.let { listOf(it) } ?: emptyList(),
                            albumArtist = cursor.getStringOrNull(albumArtistIndex),
                            album = cursor.getStringOrNull(albumIndex),
                            track = track,
                            disc = disc,
                            duration = cursor.getInt(durationIndex),
                            date = cursor.getIntOrNull(yearIndex)?.let { LocalDate(it, 1, 1) },
                            genres = emptyList(),
                            path = cursor.getString(dataIndex),
                            size = cursor.getLong(sizeIndex),
                            mimeType = cursor.getString(mimeTypeIndex),
                            dateModified = fromEpochMilliseconds(cursor.getLong(dateModifierIndex) * 1000),
                            lastPlayed = null,
                            lastCompleted = null,
                            externalId = cursor.getLong(idIndex).toString(),
                            mediaProvider = MediaProviderType.MediaStore,
                            lyrics = null,
                            grouping = null,
                            bitRate = null,
                            sampleRate = null,
                            channelCount = null,
                            composer = null,
                            replayGainAlbum = null,
                            replayGainTrack = null
                        )
                    )
                }
            }

            songs.toList()
        }
    }

    /**
     * Queries the MediaStore for genres, and updates the passed in list of SongData, producing a new list with genre information populated
     */
    private suspend fun updateGenreData(contentResolver: ContentResolver, songData: List<SongData>): List<SongData> {
        return withContext(Dispatchers.IO) {
            val mediaStoreIdSongDataMap = songData.associateBy { it.externalId }.toMutableMap()

            contentResolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.Genres._ID,
                    MediaStore.Audio.Genres.NAME
                ),
                null,
                null,
                null
            )?.use { genreCursor ->
                val genreIdIndex = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
                val nameIndex = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)

                while (isActive && genreCursor.moveToNext()) {
                    val id = genreCursor.getLong(genreIdIndex)
                    val genre = genreCursor.getString(nameIndex)

                    contentResolver.query(
                        MediaStore.Audio.Genres.Members.getContentUri("external", id),
                        arrayOf(MediaStore.Audio.Media._ID),
                        null,
                        null,
                        null
                    )?.use { genreSongCursor ->
                        val mediaIdIndex = genreSongCursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        while (isActive && genreSongCursor.moveToNext()) {
                            val mediaStoreSongId = genreSongCursor.getLong(mediaIdIndex).toString()
                            mediaStoreIdSongDataMap[mediaStoreSongId]?.let { songData ->
                                mediaStoreIdSongDataMap[mediaStoreSongId] = songData.copy(genres = songData.genres + genre)
                            }
                        }
                    }
                }
            }

            mediaStoreIdSongDataMap.values.toList()
        }
    }


    // Playlists

    private suspend fun findMediaStorePlaylists(contentResolver: ContentResolver): List<MediaStorePlaylist> {
        val mediaStorePlaylists = mutableListOf<MediaStorePlaylist>()
        contentResolver.query(
            MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.Playlists._ID,
                MediaStore.Audio.Playlists.NAME
            ),
            null,
            null,
            MediaStore.Audio.Playlists.DEFAULT_SORT_ORDER
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME)
            while (currentCoroutineContext().isActive && cursor.moveToNext()) {
                mediaStorePlaylists.add(
                    MediaStorePlaylist(
                        cursor.getLong(idIndex),
                        cursor.getString(nameIndex)
                    )
                )
            }
        }
        return mediaStorePlaylists.toList()
    }

    private suspend fun findSongsForMediaStorePlaylist(contentResolver: ContentResolver, mediaStorePlaylistId: Long): List<MediaStoreSong> {
        return withContext(Dispatchers.IO) {
            val mediaStoreSongs = mutableListOf<MediaStoreSong>()

            contentResolver.query(
                MediaStore.Audio.Playlists.Members.getContentUri("external", mediaStorePlaylistId),
                arrayOf(
                    MediaStore.Audio.Playlists.Members.TITLE,
                    MediaStore.Audio.Playlists.Members.ALBUM,
                    MediaStore.Audio.Playlists.Members.ARTIST,
                    MediaStore.Audio.Playlists.Members.DURATION,
                    MediaStore.Audio.Playlists.Members.YEAR,
                    MediaStore.Audio.Playlists.Members.TRACK,
                    MediaStore.Audio.Playlists.Members.MIME_TYPE,
                    MediaStore.Audio.Playlists.Members.DATA,
                    MediaStore.Audio.Playlists.Members.PLAY_ORDER,
                    "album_artist"
                ),
                null,
                null,
                MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER
            )?.use { cursor ->
                val playOrderIndex = cursor.getColumnIndex(MediaStore.Audio.Playlists.Members.PLAY_ORDER)
                val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.TITLE)
                val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.ALBUM)
                val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.DURATION)
                val yearIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.YEAR)
                val mimeTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.MIME_TYPE)
                val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.DATA)
                val trackIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.TRACK)

                while (isActive && cursor.moveToNext()) {
                    var track = cursor.getInt(trackIndex)
                    if (track >= 1000) {
                        track %= 1000
                    }

                    mediaStoreSongs.add(
                        MediaStoreSong(
                            playOrder = cursor.getLong(playOrderIndex),
                            title = cursor.getStringOrNull(titleIndex),
                            album = cursor.getStringOrNull(albumIndex),
                            artist = cursor.getStringOrNull(artistIndex),
                            albumArtist = cursor.getStringOrNull(cursor.getColumnIndex("album_artist")),
                            duration = cursor.getInt(durationIndex),
                            year = cursor.getIntOrNull(yearIndex),
                            track = track,
                            mimeType = cursor.getString(mimeTypeIndex),
                            path = cursor.getString(dataIndex)
                        )
                    )
                }
            }
            mediaStoreSongs.toList()
        }
    }
}

