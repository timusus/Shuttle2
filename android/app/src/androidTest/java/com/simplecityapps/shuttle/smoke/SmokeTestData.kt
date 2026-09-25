package com.simplecityapps.shuttle.smoke

import android.content.SharedPreferences
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.shuttle.model.MediaProviderType
import java.util.Date
import kotlinx.coroutines.runBlocking

/**
 * Seeds the smoke tests' library with songs from the invented sample library (android/fixtures' library.json), never
 * real artists. The names are copied here rather than read from the fixtures module, which the test APK doesn't ship.
 */
object SmokeTestData {

    /** A song the tests tap: the first in the Songs tab's default order (by album). */
    const val FIRST_SONG = "Rewind Button"

    /** An artist the tests search for. */
    const val ARTIST = "Marlow Vane"

    fun seedDatabase(database: MediaDatabase) {
        val dao = database.songDataDao()
        val songs = buildSongList()
        runBlocking {
            dao.insert(songs)
        }
    }

    fun setOnboarded(sharedPreferences: SharedPreferences) {
        sharedPreferences.edit()
            .putBoolean("thank_you_dialog_viewed", true)
            .putBoolean("changelog_show_on_launch", false)
            .commit()
    }

    private fun buildSongList(): List<SongData> {
        val now = Date()
        return listOf(
            songData(FIRST_SONG, "The Tin Orchards", "Cassette Summer", 1, 184_000, 2014, "Indie Rock", "/music/01.mp3", now),
            songData("Heatwave Radio", "The Tin Orchards", "Cassette Summer", 2, 203_000, 2014, "Indie Rock", "/music/02.mp3", now),
            songData("Borrowed Bicycle", "The Tin Orchards", "Cassette Summer", 3, 176_000, 2014, "Indie Rock", "/music/03.mp3", now),
            songData("Slipway", ARTIST, "Harbour Weather", 1, 198_000, 2016, "Folk", "/music/04.mp3", now),
            songData("Gulls Over the Co-op", ARTIST, "Harbour Weather", 2, 223_000, 2016, "Folk", "/music/05.mp3", now),
            songData("Tidewater Letter", ARTIST, "Harbour Weather", 3, 251_000, 2016, "Folk", "/music/06.mp3", now),
            songData("Chlorophyll Loop", "Juniper Static", "Phase Garden", 1, 262_000, 2021, "Electronic", "/music/07.mp3", now),
            songData("Soft Machines at Dawn", "Juniper Static", "Phase Garden", 2, 318_000, 2021, "Electronic", "/music/08.mp3", now),
            songData("Petal Arithmetic", "Juniper Static", "Phase Garden", 3, 241_000, 2021, "Electronic", "/music/09.mp3", now),
            songData("Greenhouse Effect", "Juniper Static", "Phase Garden", 4, 356_000, 2021, "Electronic", "/music/10.mp3", now),
        )
    }

    private fun songData(
        name: String,
        albumArtist: String,
        album: String,
        track: Int,
        duration: Int,
        year: Int,
        genre: String,
        path: String,
        lastModified: Date
    ): SongData = SongData(
        name = name,
        track = track,
        disc = 1,
        duration = duration,
        year = year,
        genres = listOf(genre),
        path = path,
        albumArtist = albumArtist,
        artists = listOf(albumArtist),
        album = album,
        size = 5_000_000,
        mimeType = "audio/mpeg",
        lastModified = lastModified,
        mediaProvider = MediaProviderType.Shuttle,
        lyrics = null,
        grouping = null,
        bitRate = 320,
        bitDepth = 16,
        sampleRate = 44100,
        channelCount = 2
    )
}
