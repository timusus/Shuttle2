package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.Entry
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class M3uEntryMatcherTest {
    private val albumA = song(1, "/storage/emulated/0/Music/Album A/01 - Intro.mp3")
    private val albumB = song(2, "/storage/emulated/0/Music/Album B/01 - Intro.mp3")
    private val one = song(3, "/storage/emulated/0/Music/Mix/1 - Song.mp3")
    private val eleven = song(4, "/storage/emulated/0/Music/Mix/11 - Song.mp3")
    private val index = M3uEntryMatcher.sanitisedPathsByFilename(listOf(albumA, albumB, one, eleven))

    @Test
    fun `duplicate file names resolve through their folder`() {
        M3uEntryMatcher.match(entry("Album A/01 - Intro.mp3"), index) shouldBe albumA
        M3uEntryMatcher.match(entry("Album B/01 - Intro.mp3"), index) shouldBe albumB
    }

    @Test
    fun `exact full path wins`() {
        M3uEntryMatcher.match(entry("/storage/emulated/0/Music/Album B/01 - Intro.mp3"), index) shouldBe albumB
    }

    @Test
    fun `duplicate file name with no distinguishing folder matches nothing`() {
        M3uEntryMatcher.match(entry("01 - Intro.mp3"), index) shouldBe null
    }

    @Test
    fun `similar names do not match by substring`() {
        M3uEntryMatcher.match(entry("1 - Song.mp3"), index) shouldBe one
        M3uEntryMatcher.match(entry("11 - Song.mp3"), index) shouldBe eleven
        M3uEntryMatcher.match(entry("111 - Song.mp3"), index) shouldBe null
        M3uEntryMatcher.match(entry("1 - Song.mp3"), M3uEntryMatcher.sanitisedPathsByFilename(listOf(eleven))) shouldBe null
    }

    @Test
    fun `relative entries resolve`() {
        M3uEntryMatcher.match(entry("../Album B/01 - Intro.mp3"), index) shouldBe albumB
        M3uEntryMatcher.match(entry("./Album A/../Album A/01 - Intro.mp3"), index) shouldBe albumA
    }

    @Test
    fun `windows paths and case differences resolve`() {
        M3uEntryMatcher.match(entry("C:\\Music\\ALBUM A\\01 - intro.MP3"), index) shouldBe albumA
    }

    @Test
    fun `saf document paths resolve`() {
        val saf = song(5, "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2FAlbum%20C%2F02%20-%20Track.mp3")
        val safIndex = M3uEntryMatcher.sanitisedPathsByFilename(listOf(saf))

        M3uEntryMatcher.match(entry("Album C/02 - Track.mp3"), safIndex) shouldBe saf
    }

    private fun entry(location: String) = Entry(location, null, null, null)

    private fun song(
        id: Long,
        path: String
    ) = Song(
        id = id,
        name = "Song",
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = "Album",
        track = null,
        disc = null,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = path,
        size = 0,
        mimeType = "audio/mpeg",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        mediaProvider = MediaProviderType.Shuttle,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
