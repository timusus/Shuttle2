package com.simplecityapps.shuttle.common.mediaprovider.mediastore

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import com.simplecityapps.shuttle.mediaprovider.common.MediaProvider
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Progress
import com.simplecityapps.shuttle.model.SongData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import kotlinx.datetime.LocalDate

class MediaStoreMediaProvider(
    private val context: Context
) : MediaProvider {

    override val type: MediaProviderType
        get() = MediaProviderType.MediaStore

    override fun findSongs(): Flow<MediaProvider.SongRetrievalState> {
        return flow {
            var songs = mutableListOf<SongData>()
            val cursor =
                context.contentResolver.query(
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
                )
            cursor?.use {
                val size = cursor.count
                var progress = 0
                while (currentCoroutineContext().isActive && cursor.moveToNext()) {
                    var track =
                        cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK))
                    var disc = 1
                    if (track >= 1000) {
                        track %= 1000

                        disc = track / 1000
                        if (disc == 0) {
                            disc = 1
                        }
                    }

                    val songData = cursor.toSongData()
                    songs.add(songData)
                    progress++
                    emit(
                        MediaProvider.SongRetrievalState.ReadingSongData(
                            songData = songData,
                            progress = Progress(progress, size)
                        )
                    )
                    delay(20)
                }
            }

            context.contentResolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                null,
                null,
                null
            )?.use { genreCursor ->
                while (currentCoroutineContext().isActive && genreCursor.moveToNext()) {
                    val id =
                        genreCursor.getLong(genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID))
                    val genre =
                        genreCursor.getString(genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME))
                    context.contentResolver.query(
                        MediaStore.Audio.Genres.Members.getContentUri("external", id),
                        arrayOf(MediaStore.Audio.Media._ID),
                        null,
                        null,
                        null
                    )?.use { genreSongCursor ->
                        while (currentCoroutineContext().isActive && genreSongCursor.moveToNext()) {
                            val songId = genreSongCursor.getLong(
                                genreSongCursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                            ).toString()
                            songs = songs.map { song ->
                                if (song.externalId == songId) {
                                    song.copy(genres = song.genres + genre)
                                } else {
                                    song
                                }
                            }.toMutableList()
                        }
                    }
                }
            }
            emit(MediaProvider.SongRetrievalState.Complete(songs))
        }.catch {
            println("Exception: ${it.localizedMessage}")
        }
    }

    data class MediaStoreSong(
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

//    override fun findPlaylists(existingSongs: List<Song>): Flow<ProgressState<PlaylistData>> {
//        return flow {
//            val mediaStorePlaylists = findMediaStorePlaylists().toList()
//            val updates = mediaStorePlaylists.mapIndexed { i, mediaStorePlaylist ->
//                val mediaStoreSongs = findSongsForMediaStorePlaylist(mediaStorePlaylist.id)
//
//                // Associate Media Store songs with Shuttle's songs
//                val matchingSongs = mediaStoreSongs.mapNotNull { mediaStoreSong ->
//                    existingSongs.firstOrNull { existingSong ->
//                        // We assume two songs are equal, if they have the same title, album, artist & duration. We can't be too specific, as the
//                        // MediaStore scanner may have interpreted some fields differently to Shuttle's built in scanner.
//                        existingSong.name.equals(mediaStoreSong.title, ignoreCase = true)
//                                && existingSong.album.equals(mediaStoreSong.album, ignoreCase = true)
//                                && (existingSong.artists.any { it.equals(mediaStoreSong.artist, true) } || existingSong.albumArtist.equals(mediaStoreSong.albumArtist, ignoreCase = true))
//                                && abs((existingSong.duration ?: 0) - mediaStoreSong.duration) <= 1000 // song duration is within 1 second
//                    }
//                }
//
//                val playlist = PlaylistData(mediaStorePlaylist.name, type, mediaStorePlaylist.id.toString(), matchingSongs)
//                emit(ProgressState.InProgress(playlist, Progress(i, mediaStorePlaylists.size)))
//                playlist
//            }
//
//            emit(ProgressState.Success(updates.toList()))
//        }
//    }

    private suspend fun findSongsForMediaStorePlaylist(mediaStorePlaylistId: Long): List<MediaStoreSong> {
        return withContext(Dispatchers.IO) {
            val songs = mutableListOf<MediaStoreSong>()

            val cursor = context.contentResolver.query(
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

                    var track =
                        cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK))
                    if (track >= 1000) {
                        track %= 1000
                    }

                    songs.add(
                        MediaStoreSong(
                            playOrder = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Playlists.Members.PLAY_ORDER)),
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


    data class MediaStorePlaylist(val id: Long, val name: String)

    private fun findMediaStorePlaylists(): Flow<MediaStorePlaylist> {
        return flow {

            val cursor = context.contentResolver.query(
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
}

// Todo: Cache 'getColumnIndex()' calls
fun Cursor.toSongData(): SongData {
    var track =
        getInt(getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK))
    var disc = 1
    if (track >= 1000) {
        track %= 1000

        disc = track / 1000
        if (disc == 0) {
            disc = 1
        }
    }
    return SongData(
        name = getStringOrNull(
            getColumnIndexOrThrow(
                MediaStore.Audio.Media.TITLE
            )
        ),
        artists = getStringOrNull(
            getColumnIndexOrThrow(
                MediaStore.Audio.Media.ARTIST
            )
        )?.let { listOf(it) } ?: emptyList(),
        albumArtist = getStringOrNull(getColumnIndex("album_artist")),
        album = getStringOrNull(
            getColumnIndexOrThrow(
                MediaStore.Audio.Media.ALBUM
            )
        ),
        track = track,
        disc = disc,
        duration = getInt(getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
        date = getIntOrNull(getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR))?.let { LocalDate(it, 1, 1) },
        genres = emptyList(),
        path = getString(getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)),
        size = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)),
        mimeType = getString(getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)),
        dateModified = fromEpochMilliseconds(
            getLong(
                getColumnIndexOrThrow(
                    MediaStore.Audio.Media.DATE_MODIFIED
                )
            ) * 1000
        ),
        lastPlayed = null,
        lastCompleted = null,
        externalId = getLong(
            getColumnIndexOrThrow(
                MediaStore.Audio.Media._ID
            )
        ).toString(),
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
}