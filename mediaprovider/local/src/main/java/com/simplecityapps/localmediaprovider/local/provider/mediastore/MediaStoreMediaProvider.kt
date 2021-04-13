package com.simplecityapps.localmediaprovider.local.provider.mediastore

import android.content.Context
import android.provider.MediaStore
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.*

class MediaStoreMediaProvider(
    private val context: Context
) : MediaProvider {

    override val type: MediaProvider.Type
        get() = MediaProvider.Type.MediaStore

    override suspend fun findSongs(callback: ((song: Song, progress: Int, total: Int) -> Unit)?): List<Song> {

        var songs = mutableListOf<Song>()
        withContext(Dispatchers.IO) {
            val songCursor = context.contentResolver.query(
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

            songCursor?.use {
                val size = songCursor.count
                var progress = 0
                while (coroutineContext.isActive && songCursor.moveToNext()) {
                    var track = songCursor.getInt(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK))
                    var disc = 1
                    if (track >= 1000) {
                        track %= 1000

                        disc = track / 1000
                        if (disc == 0) {
                            disc = 1
                        }
                    }

                    val song = Song(
                        id = 0,
                        name = songCursor.getStringOrNull(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)),
                        artists = songCursor.getStringOrNull(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))?.let { listOf(it) } ?: emptyList(),
                        albumArtist = songCursor.getStringOrNull(songCursor.getColumnIndex("album_artist")),
                        album = songCursor.getStringOrNull(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)),
                        track = track,
                        disc = disc,
                        duration = songCursor.getInt(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
                        year = songCursor.getIntOrNull(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)),
                        genres = emptyList(),
                        path = songCursor.getString(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)),
                        size = songCursor.getLong(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)),
                        mimeType = songCursor.getString(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)),
                        lastModified = Date(songCursor.getLong(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)) * 1000),
                        lastPlayed = null,
                        lastCompleted = null,
                        playCount = 0,
                        playbackPosition = 0,
                        blacklisted = false,
                        mediaStoreId = songCursor.getLong(songCursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)),
                        mediaProvider = type
                    )
                    songs.add(song)
                    progress++
                    withContext(Dispatchers.Main) {
                        callback?.invoke(song, progress, size)
                    }
                }
            }

            context.contentResolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                null,
                null,
                null
            )?.use { genreCursor ->
                while (coroutineContext.isActive && genreCursor.moveToNext()) {
                    val id = genreCursor.getLong(genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID))
                    val genre = genreCursor.getString(genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME))
                    context.contentResolver.query(
                        MediaStore.Audio.Genres.Members.getContentUri("external", id),
                        arrayOf(MediaStore.Audio.Media._ID),
                        null,
                        null,
                        null
                    )?.use { genreSongCursor ->
                        while (coroutineContext.isActive && genreSongCursor.moveToNext()) {
                            val songId = genreSongCursor.getLong(genreSongCursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                            songs = songs.map { song ->
                                if (song.mediaStoreId == songId) {
                                    song.copy(genres = song.genres + genre)
                                } else {
                                    song
                                }
                            }.toMutableList()
                        }
                    }
                }
            }
        }

        return songs
    }
}