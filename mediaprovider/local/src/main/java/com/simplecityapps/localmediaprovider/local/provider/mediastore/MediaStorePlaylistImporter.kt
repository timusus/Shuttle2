package com.simplecityapps.localmediaprovider.local.provider.mediastore

import android.content.Context
import android.provider.MediaStore
import com.simplecityapps.mediaprovider.repository.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.SongRepository
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single

class MediaStorePlaylistImporter(
    private val context: Context,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository
) {

    fun importPlaylists(): Completable {
        return songRepository.getSongs().first(emptyList()).flatMapCompletable { allSongs ->
            playlistRepository.getPlaylists().first(emptyList()).flatMapCompletable { allPlaylists ->

                // Get the MediaStore playlists, and their songs
                findMediaStorePlaylists()
                    .flatMapSingle { playlist ->
                        findSongsForPlaylist(playlist.id)
                            .map { songs -> Pair(playlist, songs) }
                    }
                    .flatMapCompletable { (mediaStorePlaylist: MediaStorePlaylist, mediaStoreSongs: List<MediaStoreSong>) ->
                        // Associate Media Store songs with Shuttle's songs
                        val matchingSongs = allSongs.filter { song ->
                            mediaStoreSongs.any { mediaStoreSong ->
                                // We assume two songs are equal, if they have the same title, album, artist & duration. We can't be too specific, as the
                                // MediaStore scanner may have interpreted some fields differently to Shuttle's built in scanner.
                                song.name.equals(mediaStoreSong.title, ignoreCase = true)
                                        && song.albumName.equals(mediaStoreSong.album, ignoreCase = true)
                                        && song.albumArtistName.equals(mediaStoreSong.albumArtist, ignoreCase = true)
                                        && song.duration == mediaStoreSong.duration
                            }
                        }
                        if (matchingSongs.isNotEmpty()) {
                            // We have a list of songs to import
                            allPlaylists.find { playlist -> playlist.mediaStoreId == mediaStorePlaylist.id || playlist.name == mediaStorePlaylist.name }?.let { existingPlaylist ->
                                playlistRepository.getSongsForPlaylist(existingPlaylist.id)
                                    .first(emptyList())
                                    .flatMapCompletable { existingSongs ->
                                        val duplicates = existingSongs.intersect(matchingSongs)
                                        val songsToInsert = matchingSongs.toMutableList()
                                        songsToInsert.removeAll(duplicates)
                                        if (songsToInsert.isEmpty()) {
                                            Completable.complete()
                                        } else {
                                            playlistRepository.updatePlaylistMediaStoreId(existingPlaylist, mediaStorePlaylist.id)
                                                .andThen(playlistRepository.addToPlaylist(existingPlaylist, songsToInsert))
                                        }
                                    }
                            } ?: run {
                                playlistRepository.createPlaylist(mediaStorePlaylist.name, mediaStorePlaylist.id, matchingSongs).ignoreElement()
                            }
                        } else {
                            Completable.complete()
                        }
                    }
            }
        }
    }


    data class MediaStorePlaylist(val id: Long, val name: String)

    private fun findMediaStorePlaylists(): Observable<MediaStorePlaylist> {
        return Observable.create { emitter ->
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
                try {
                    while (cursor.moveToNext()) {
                        if (emitter.isDisposed) {
                            return@use
                        }
                        emitter.onNext(
                            MediaStorePlaylist(
                                cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME))
                            )
                        )
                    }
                } catch (e: Exception) {
                    emitter.onError(e)
                }
            }
            emitter.onComplete()
        }
    }


    data class MediaStoreSong(val title: String, val album: String, val albumArtist: String, val duration: Int, val year: Int, val track: Int, val mimeType: String, val path: String)

    private fun findSongsForPlaylist(playlistId: Long): Single<List<MediaStoreSong>> {
        return Single.create<List<MediaStoreSong>> { emitter ->

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
                try {
                    while (cursor.moveToNext()) {
                        if (emitter.isDisposed) {
                            return@use
                        }

                        val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                        val albumArtist = cursor.getString(cursor.getColumnIndex("album_artist")) ?: artist

                        var track = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK))
                        if (track >= 1000) {
                            track %= 1000
                        }

                        songs.add(
                            MediaStoreSong(
                                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.TITLE)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.ALBUM)),
                                albumArtist,
                                cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.DURATION)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.YEAR)),
                                track,
                                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.MIME_TYPE)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.DATA))
                            )
                        )
                    }
                } catch (e: Exception) {
                    emitter.onError(e)
                }
            }

            emitter.onSuccess(songs)
        }
    }
}