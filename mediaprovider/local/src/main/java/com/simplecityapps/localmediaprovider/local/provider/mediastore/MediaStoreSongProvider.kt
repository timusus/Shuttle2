package com.simplecityapps.localmediaprovider.local.provider.mediastore

import android.content.Context
import android.provider.MediaStore
import com.simplecityapps.mediaprovider.SongProvider
import com.simplecityapps.mediaprovider.model.Song
import io.reactivex.Observable
import java.util.*

class MediaStoreSongProvider(
    private val context: Context
) : SongProvider {

    override fun findSongs(): Observable<Pair<Song, Float>> {
        return Observable.create { emitter ->
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
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
                try {
                    while (cursor.moveToNext()) {
                        if (emitter.isDisposed) {
                            return@use
                        }
                        val album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM))
                        val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                        val albumArtist = cursor.getString(cursor.getColumnIndex("album_artist")) ?: artist

                        var track = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK))
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
                            name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)),
                            albumArtistId = 0,
                            albumArtistName = albumArtist,
                            albumId = 0,
                            albumName = album,
                            track = track,
                            disc = disc,
                            duration = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
                            year = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)),
                            path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)),
                            size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)),
                            mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)),
                            lastModified = Date(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)) * 1000),
                            lastPlayed = null,
                            lastCompleted = null,
                            playCount = 0,
                            playbackPosition = 0,
                            blacklisted = false
                        )
                        emitter.onNext(Pair(song, cursor.position / cursor.count.toFloat()))
                    }
                } catch (e: Exception) {
                    emitter.onError(e)
                }
            }
            emitter.onComplete()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MediaStoreSongProvider

        if (context != other.context) return false

        return true
    }

    override fun hashCode(): Int {
        return context.hashCode()
    }
}