package com.simplecityapps.localmediaprovider.local.provider.mediastore

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import com.simplecityapps.localmediaprovider.local.provider.FolderImageReader
import com.simplecityapps.localmediaprovider.local.provider.localArtworkVersion
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

class MediaStoreMediaProvider(
    private val context: Context,
    private val replayGainReader: MediaStoreReplayGainReader,
    private val preferenceManager: GeneralPreferenceManager
) : MediaProvider {
    override val type = MediaProviderType.MediaStore

    // Songs

    override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = flow {
        val rawSongs = mutableListOf<Song>()
        val projection =
            mutableListOf(
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
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projection.add(MediaStore.Audio.Media.DISC_NUMBER)
        }
        val songCursor =
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection.toTypedArray(),
                "${MediaStore.Audio.Media.IS_MUSIC}=1 OR ${MediaStore.Audio.Media.IS_PODCAST}=1",
                null,
                null
            )

        songCursor?.use {
            val folderImageReader = FolderImageReader()
            val discNumberColumnIndex =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    songCursor.getColumnIndex(MediaStore.Audio.Media.DISC_NUMBER)
                } else {
                    -1
                }
            while (currentCoroutineContext().isActive && songCursor.moveToNext()) {
                val rawTrack =
                    songCursor.getInt(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK))
                val discNumberColumnValue =
                    if (discNumberColumnIndex != -1) songCursor.getStringOrNull(discNumberColumnIndex) else null
                val (disc, track) = decodeDiscTrack(rawTrack, discNumberColumnValue)
                val path = songCursor.getString(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                val lastModified = songCursor.getLong(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)) * 1000

                val song =
                    Song(
                        id = 0,
                        name =
                            songCursor.getStringOrNull(
                                songCursor.getColumnIndexOrThrow(
                                    MediaStore.Audio.Media.TITLE
                                )
                            ),
                        artists =
                            songCursor.getStringOrNull(
                                songCursor.getColumnIndexOrThrow(
                                    MediaStore.Audio.Media.ARTIST
                                )
                            )?.let { listOf(it) } ?: emptyList(),
                        albumArtist = songCursor.getStringOrNull(songCursor.getColumnIndex("album_artist")),
                        album =
                            songCursor.getStringOrNull(
                                songCursor.getColumnIndexOrThrow(
                                    MediaStore.Audio.Media.ALBUM
                                )
                            ),
                        track = track,
                        disc = disc,
                        duration = songCursor.getInt(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
                        date = songCursor.getIntOrNull(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR))?.let { LocalDate(it, 1, 1) },
                        genres = emptyList(),
                        path = path,
                        size = songCursor.getLong(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)),
                        mimeType = songCursor.getString(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)),
                        lastModified = Instant.fromEpochMilliseconds(lastModified),
                        lastPlayed = null,
                        lastCompleted = null,
                        playCount = 0,
                        playbackPosition = 0,
                        blacklisted = false,
                        externalId =
                            songCursor.getLong(
                                songCursor.getColumnIndexOrThrow(
                                    MediaStore.Audio.Media._ID
                                )
                            ).toString(),
                        mediaProvider = type,
                        lyrics = null,
                        grouping = null,
                        bitRate = null,
                        bitDepth = null,
                        sampleRate = null,
                        channelCount = null,
                        artworkVersion = localArtworkVersion(lastModified, folderImageReader.imagesNear(path))
                    )
                rawSongs.add(song)
            }
        }

        var songs = mutableListOf<Song>()
        val backfillReplayGain = !preferenceManager.mediaStoreReplayGainBackfilled
        rawSongs
            .withReplayGainTags(existingSongs, replayGainReader, readUnchanged = backfillReplayGain)
            .collectIndexed { index, song ->
                songs.add(song)
                emit(
                    FlowEvent.Progress(
                        MessageProgress(
                            message =
                                listOf(
                                    song.friendlyArtistName ?: song.albumArtist,
                                    song.name
                                ).joinToString(" • "),
                            progress = Progress(index + 1, rawSongs.size)
                        )
                    )
                )
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
                        val songId =
                            genreSongCursor.getLong(
                                genreSongCursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                            ).toString()
                        songs =
                            songs.map { song ->
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
        emit(FlowEvent.Success(songs))
        // Only once the importer has handled the result, so an import cancelled part way through backfills again next time
        if (backfillReplayGain) {
            preferenceManager.mediaStoreReplayGainBackfilled = true
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

    // Playlists

    override fun findPlaylists(
        existingPlaylists: List<Playlist>,
        existingSongs: List<Song>
    ): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> = flow {
        val mediaStorePlaylists = findMediaStorePlaylists().toList()
        val updates =
            mediaStorePlaylists.mapIndexed { i, mediaStorePlaylist ->
                val mediaStoreSongs = findSongsForMediaStorePlaylist(mediaStorePlaylist.id)

                // Associate Media Store songs with Shuttle's songs
                val matchingSongs =
                    mediaStoreSongs.mapNotNull { mediaStoreSong ->
                        existingSongs.firstOrNull { existingSong ->
                            // We assume two songs are equal, if they have the same title, album, artist & duration. We can't be too specific, as the
                            // MediaStore scanner may have interpreted some fields differently to Shuttle's built in scanner.
                            existingSong.name.equals(mediaStoreSong.title, ignoreCase = true) &&
                                existingSong.album.equals(mediaStoreSong.album, ignoreCase = true) &&
                                (existingSong.artists.any { it.equals(mediaStoreSong.artist, true) } || existingSong.albumArtist.equals(mediaStoreSong.albumArtist, ignoreCase = true)) &&
                                abs(existingSong.duration - mediaStoreSong.duration) <= 1000 // song duration is within 1 second
                        }
                    }

                val updateData =
                    MediaImporter.PlaylistUpdateData(
                        type,
                        mediaStorePlaylist.name,
                        matchingSongs,
                        mediaStorePlaylist.id.toString()
                    )
                emit(FlowEvent.Progress(MessageProgress("Found playlist", Progress(i, mediaStorePlaylists.size))))
                updateData
            }

        emit(FlowEvent.Success(updates.toList()))
    }

    private suspend fun findSongsForMediaStorePlaylist(mediaStorePlaylistId: Long): List<MediaStoreSong> {
        return withContext(Dispatchers.IO) {
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
