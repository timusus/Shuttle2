package com.simplecityapps.shuttle.smoke

import android.content.SharedPreferences
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.shuttle.model.MediaProviderType
import java.util.Date
import kotlinx.coroutines.runBlocking

object SmokeTestData {

    fun seedDatabase(database: MediaDatabase) {
        val dao = database.songDataDao()
        val songs = buildSongList()
        runBlocking {
            dao.insert(songs)
        }
    }

    fun setOnboarded(sharedPreferences: SharedPreferences) {
        sharedPreferences.edit()
            .putBoolean("has_onboarded", true)
            .commit()
    }

    private fun buildSongList(): List<SongData> {
        val now = Date()
        return listOf(
            songData("Highway to Hell", "AC/DC", "Back in Black", 1, 210000, "/music/01.mp3", now),
            songData("Thunderstruck", "AC/DC", "The Razors Edge", 1, 292000, "/music/02.mp3", now),
            songData("Back in Black", "AC/DC", "Back in Black", 2, 255000, "/music/03.mp3", now),
            songData("Bohemian Rhapsody", "Queen", "A Night at the Opera", 1, 354000, "/music/04.mp3", now),
            songData("Don't Stop Me Now", "Queen", "Jazz", 1, 209000, "/music/05.mp3", now),
            songData("Somebody to Love", "Queen", "A Day at the Races", 1, 297000, "/music/06.mp3", now),
            songData("Stairway to Heaven", "Led Zeppelin", "Led Zeppelin IV", 4, 482000, "/music/07.mp3", now),
            songData("Whole Lotta Love", "Led Zeppelin", "Led Zeppelin II", 1, 333000, "/music/08.mp3", now),
            songData("Black Dog", "Led Zeppelin", "Led Zeppelin IV", 1, 296000, "/music/09.mp3", now),
            songData("Immigrant Song", "Led Zeppelin", "Led Zeppelin III", 1, 146000, "/music/10.mp3", now),
        )
    }

    private fun songData(
        name: String,
        albumArtist: String,
        album: String,
        track: Int,
        duration: Int,
        path: String,
        lastModified: Date
    ): SongData = SongData(
        name = name,
        track = track,
        disc = 1,
        duration = duration,
        year = 1975,
        genres = listOf("Rock"),
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
