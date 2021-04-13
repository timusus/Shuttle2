package com.simplecityapps.localmediaprovider.local.provider.mediastore

import android.content.Context
import android.provider.MediaStore
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import com.simplecityapps.mediaprovider.repository.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs

class MediaStorePlaylistImporter(
    private val context: Context,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository
) {

    suspend fun importPlaylists() {
        val allSongs = songRepository.getSongs(SongQuery.All()).firstOrNull().orEmpty()
        val allPlaylists = playlistRepository.getPlaylists(PlaylistQuery.All()).firstOrNull().orEmpty()

        findMediaStorePlaylists()
            .map { playlist ->
                Pair(playlist, findSongsForPlaylist(playlist.id))
            }
            .collect { (mediaStorePlaylist, mediaStoreSongs) ->
                // Associate Media Store songs with Shuttle's songs
                val matchingSongs = allSongs.filter { song ->
                    mediaStoreSongs.any { mediaStoreSong ->
                        // We assume two songs are equal, if they have the same title, album, artist & duration. We can't be too specific, as the
                        // MediaStore scanner may have interpreted some fields differently to Shuttle's built in scanner.
                        song.name.equals(mediaStoreSong.title, ignoreCase = true)
                                && song.album.equals(mediaStoreSong.album, ignoreCase = true)
                                && song.albumArtist.equals(mediaStoreSong.albumArtist, ignoreCase = true)
                                && abs(song.duration - mediaStoreSong.duration) <= 1000 // song duration is within 1 second
                    }
                }

                if (matchingSongs.isNotEmpty()) {
                    // We have a list of songs to import
                    allPlaylists.find { playlist -> playlist.mediaStoreId == mediaStorePlaylist.id || playlist.name == mediaStorePlaylist.name }?.let { existingPlaylist ->
                        val existingSongs = playlistRepository.getSongsForPlaylist(existingPlaylist.id)
                            .firstOrNull()
                            .orEmpty()

                        val duplicates = existingSongs.intersect(matchingSongs)
                        val songsToInsert = matchingSongs.toMutableList()
                        songsToInsert.removeAll(duplicates)
                        if (songsToInsert.isNotEmpty()) {
                            playlistRepository.updatePlaylistMediaStoreId(existingPlaylist, mediaStorePlaylist.id)
                            playlistRepository.addToPlaylist(existingPlaylist, songsToInsert)
                        }
                    } ?: run {
                        playlistRepository.createPlaylist(mediaStorePlaylist.name, mediaStorePlaylist.id, matchingSongs)
                    }
                }
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
                null
            )

            cursor?.use {
                while (cursor.moveToNext()) {
                    if (!currentCoroutineContext().isActive) {
                        return@use
                    }
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


    data class MediaStoreSong(
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

    private suspend fun findSongsForPlaylist(playlistId: Long): List<MediaStoreSong> {
        return withContext(Dispatchers.IO) {
            val songs = mutableListOf<MediaStoreSong>()

            val cursor = context.contentResolver.query(
                MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId),
                arrayOf(
                    MediaStore.Audio.Playlists.Members.TITLE,
                    MediaStore.Audio.Playlists.Members.ALBUM,
                    MediaStore.Audio.Playlists.Members.ARTIST,
                    MediaStore.Audio.Playlists.Members.DURATION,
                    MediaStore.Audio.Playlists.Members.YEAR,
                    MediaStore.Audio.Media.TRACK,
                    MediaStore.Audio.Playlists.Members.MIME_TYPE,
                    MediaStore.Audio.Playlists.Members.DATA,
                    "album_artist"
                ),
                null,
                null,
                null
            )

            cursor?.use {
                while (cursor.moveToNext()) {
                    if (!isActive) {
                        return@use
                    }

                    var track = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK))
                    if (track >= 1000) {
                        track %= 1000
                    }

                    songs.add(
                        MediaStoreSong(
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
}