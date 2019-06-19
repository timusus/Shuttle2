package com.simplecityapps.localmediaprovider.local.provider.mediastore

import android.content.Context
import android.provider.MediaStore
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.localmediaprovider.local.provider.SongProvider
import io.reactivex.Single
import java.util.*

class MediaStoreSongProvider(
    private val context: Context
) : SongProvider {

    override fun findSongs(): Single<List<SongData>> {
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
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.IS_PODCAST,
                MediaStore.Audio.Media.BOOKMARK,
                "album_artist"
            ),
            "${MediaStore.Audio.Media.IS_MUSIC}=1 OR ${MediaStore.Audio.Media.IS_PODCAST}=1",
            null,
            null
        )

        cursor?.use { cursor ->
            val songDataList = mutableListOf<SongData>()
            try {
                while (cursor.moveToNext()) {
                    val album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM))
                    val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                    val albumArtist = cursor.getString(cursor.getColumnIndex("album_artist")) ?: artist

                    val songData = SongData(
                        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)),
                        0,
                        cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)),
                        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)),
                        0,
                        0,
                        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)),
                        Date(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED))),
                        cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.BOOKMARK)),
                        0,
                        null,
                        null
                    )
                    songData.albumName = album
                    songData.albumArtistName = albumArtist

                    songDataList.add(songData)
                }
                return Single.just(songDataList)
            } catch (e: Exception) {
                return Single.error(e)
            }
        }

        return Single.just(emptyList())
    }
}